package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.tesis.vimed.api.FiltroSeguridad;
import com.tesis.vimed.api.OpenAIClient;
import com.tesis.vimed.api.PromptVita;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.MensajeChatDAO;
import com.tesis.vimed.utils.ModoPaciente;
import com.tesis.vimed.utils.NavInferior;
import com.tesis.vimed.models.Horario;
import com.tesis.vimed.models.MensajeChat;
import com.tesis.vimed.models.Medicamento;

import java.util.ArrayList;
import java.util.List;

public class ChatbotActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private ScrollView scrollChat;
    private EditText etMessage;
    private View suggestedPrompts;

    /**
     * Cuántos mensajes del historial viajan a la API.
     *
     * Antes iban TODOS, siempre: el historial se carga de la base al abrir
     * el chat y crece para siempre, así que en el mensaje cien se pagaba
     * por reenviar los noventa y nueve anteriores. El costo crecía al
     * cuadrado.
     *
     * Treinta y no diez porque lo caro era la falta de límite, no el
     * tamaño: pasar de diez a treinta cuesta centavos y evita perder una
     * aclaración que la persona hizo hace un rato. Y lo durable —qué
     * medicamentos toma, a qué hora— no depende de esto: el prompt del
     * sistema lo reconstruye desde la base en cada llamada.
     */
    private static final int MENSAJES_DE_CONTEXTO = 30;

    private com.tesis.vimed.utils.VozVita voz;
    private com.tesis.vimed.utils.GrabadorVoz grabador;
    private androidx.activity.result.ActivityResultLauncher<String> pedirMicrofono;
    private View typingBubble;

    private SessionManager sessionManager;
    private OpenAIClient openAIClient;
    private MensajeChatDAO chatDAO;
    private List<MensajeChat> historial;
    private int idUsuario;
    private boolean isLoading = false;

    /**
     * Los medicamentos que Vita conoce, traídos del servidor.
     *
     * ANTES SE LEÍAN DE LA BASE LOCAL y por eso Vita nunca veía ninguno:
     * los medicamentos viven en Supabase, y todas las demás pantallas los
     * piden por VimedRepo. La tabla local de medicamentos no se llena
     * nunca. El chat era el único que la miraba, así que contestaba
     * "decime qué medicamentos tomás" a alguien que los tenía cargados
     * hacía meses.
     *
     * Se traen una vez al abrir la pantalla y quedan acá: armar el prompt
     * pasa en el hilo principal y no puede esperar a la red.
     */
    private final List<Medicamento> misMedicamentos = new ArrayList<>();
    private final java.util.Map<Integer, List<Horario>> misHorarios = new java.util.HashMap<>();

    /**
     * Si la consulta al servidor todavía no volvió, es distinto de que no
     * haya medicamentos, y al modelo hay que decirle cuál de las dos es.
     */
    private boolean medicamentosCargados = false;

    /**
     * De quién es la medicación de la que habla Vita. Puede ser la del
     * paciente cuando abre el chat el cuidador.
     */
    private ModoPaciente modo = ModoPaciente.propio();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        sessionManager = new SessionManager(this);
        idUsuario = sessionManager.getIdUsuario();
        modo = ModoPaciente.de(this);
        openAIClient = new OpenAIClient();
        chatDAO = new MensajeChatDAO(DatabaseHelper.getInstance(this));

        chatContainer = findViewById(R.id.chat_container);
        scrollChat = findViewById(R.id.scroll_chat);
        etMessage = findViewById(R.id.et_message);
        suggestedPrompts = findViewById(R.id.suggested_prompts);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        setupBottomNav();
        findViewById(R.id.btn_send).setOnClickListener(v -> enviarMensaje());
        voz = new com.tesis.vimed.utils.VozVita(this, hablando -> { });
        grabador = new com.tesis.vimed.utils.GrabadorVoz();

        // El permiso se registra acá, antes de que la Activity llegue a
        // RESUMED; hacerlo dentro del onClick tira IllegalStateException.
        pedirMicrofono = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            concedido -> {
                if (concedido) empezarAGrabar();
                else Toast.makeText(this,
                    "Sin permiso del micrófono no puedo escucharte. Podés "
                        + "escribir igual.", Toast.LENGTH_LONG).show();
            });

        findViewById(R.id.btn_mic).setOnClickListener(v -> alTocarMicrofono());
        findViewById(R.id.btn_clear_chat).setOnClickListener(v -> confirmarLimpiarHistorial());

        // Chips de sugerencias
        findViewById(R.id.chip_1).setOnClickListener(v ->
            enviarTexto("¿Puedo tomar pastillas con café?"));
        findViewById(R.id.chip_2).setOnClickListener(v ->
            enviarTexto("¿Qué hago si olvidé una toma?"));
        findViewById(R.id.chip_3).setOnClickListener(v ->
            enviarTexto("¿Cuáles son los efectos secundarios comunes de los medicamentos?"));
        findViewById(R.id.chip_4).setOnClickListener(v ->
            enviarTexto("¿A qué hora debo tomar mis medicamentos?"));

        // Cargar historial existente
        cargarHistorial();
        cargarMedicamentos();
    }

    /**
     * Trae del servidor los medicamentos y sus horarios, para que Vita sepa
     * de qué le hablan.
     *
     * Se pide una sola vez al abrir el chat y no antes de cada mensaje: la
     * lista no cambia mientras se conversa, y una consulta por mensaje
     * sumaría espera a cada respuesta.
     *
     * Si falla, el chat sigue andando sin la lista. Vita responde igual las
     * preguntas generales, que son la mayoría; quedarse sin chat porque no
     * se pudo leer la lista sería peor que quedarse sin la lista.
     */
    private void cargarMedicamentos() {
        if (idUsuario == -1) { medicamentosCargados = true; return; }

        com.tesis.vimed.api.VimedRepo.Cb<List<Medicamento>> cb =
            new com.tesis.vimed.api.VimedRepo.Cb<List<Medicamento>>() {
                @Override public void onOk(List<Medicamento> meds) {
                    misMedicamentos.clear();
                    misMedicamentos.addAll(meds);
                    if (meds.isEmpty()) { medicamentosCargados = true; return; }

                    List<Integer> ids = new ArrayList<>();
                    for (Medicamento m : meds) ids.add(m.getId());

                    com.tesis.vimed.api.VimedRepo.listarHorariosDe(ids,
                        new com.tesis.vimed.api.VimedRepo.Cb<List<Horario>>() {
                            @Override public void onOk(List<Horario> horarios) {
                                for (Horario h : horarios) {
                                    List<Horario> del = misHorarios.get(h.getIdMedicamento());
                                    if (del == null) {
                                        del = new ArrayList<>();
                                        misHorarios.put(h.getIdMedicamento(), del);
                                    }
                                    del.add(h);
                                }
                                medicamentosCargados = true;
                            }
                            // Sin horarios igual sirve: sabe qué toma
                            // aunque no sepa a qué hora.
                            @Override public void onError(String msg) {
                                medicamentosCargados = true;
                            }
                        });
                }
                @Override public void onError(String msg) {
                    medicamentosCargados = true;
                }
            };

        // Abierto por el cuidador, Vita tiene que hablar de la medicación
        // DEL PACIENTE. Antes cargaba siempre la de la cuenta logueada, así
        // que a la hija le contestaba que no tenía nada cargado mientras su
        // madre tenía cinco medicamentos.
        if (modo.esDeOtro()) {
            com.tesis.vimed.api.VimedRepo.listarMedicamentosDe(modo.idUsuario, cb);
        } else {
            com.tesis.vimed.api.VimedRepo.listarMedicamentos(this, cb);
        }
    }

    private void cargarHistorial() {
        historial = idUsuario != -1
            ? chatDAO.listarPorUsuario(idUsuario)
            : new ArrayList<>();

        if (historial.isEmpty()) {
            suggestedPrompts.setVisibility(View.VISIBLE);
        } else {
            suggestedPrompts.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (MensajeChat msg : historial) {
                agregarBurbuja(inflater, msg.getContenido(), msg.esDelBot());
            }
            scrollAlFinal();
        }
    }

    private void enviarMensaje() {
        String texto = etMessage.getText().toString().trim();
        if (texto.isEmpty() || isLoading) return;
        etMessage.setText("");
        ocultarTeclado();
        enviarTexto(texto);
    }

    private void enviarTexto(String texto) {
        if (isLoading) return;

        // ANTES de gastar una llamada: hay dos cosas que la app contesta
        // sola, porque una respuesta equivocada ahí le hace daño a alguien
        // y un modelo puede tener un mal día. Ver FiltroSeguridad.
        FiltroSeguridad.Resultado filtro = FiltroSeguridad.revisar(texto);
        if (filtro.loRespondeLaApp()) {
            responderSinModelo(texto, filtro.respuesta);
            return;
        }

        // Sin clave configurada no tiene sentido intentar: se falla con un
        // mensaje que dice qué hacer, en vez de con un error de red que
        // manda a revisar el wifi.
        if (!OpenAIClient.hayClave()) {
            responderSinModelo(texto,
                "Todavía no estoy configurada en esta instalación. Falta "
                    + "cargar la clave del asistente en local.properties "
                    + "(OPENAI_API_KEY) y volver a compilar.");
            return;
        }

        isLoading = true;
        suggestedPrompts.setVisibility(View.GONE);

        // Guardar + mostrar mensaje del usuario
        MensajeChat msgUsuario = new MensajeChat(idUsuario, "usuario", texto);
        historial.add(msgUsuario);
        if (idUsuario != -1) chatDAO.insertar(msgUsuario);

        LayoutInflater inflater = LayoutInflater.from(this);
        agregarBurbuja(inflater, texto, false);

        // Mostrar indicador "escribiendo..."
        typingBubble = inflater.inflate(R.layout.item_chat_bubble_vita, chatContainer, false);
        ((TextView) typingBubble.findViewById(R.id.tv_message)).setText("•••");
        chatContainer.addView(typingBubble);
        scrollAlFinal();

        // Llamar a la API, con SOLO los últimos mensajes.
        String systemPrompt = construirSystemPrompt();
        openAIClient.enviarMensaje(systemPrompt, ultimosMensajes(), new OpenAIClient.Callback() {
            @Override
            public void onRespuesta(String respuesta) {
                isLoading = false;
                chatContainer.removeView(typingBubble);

                MensajeChat msgBot = new MensajeChat(idUsuario, "bot", respuesta);
                historial.add(msgBot);
                if (idUsuario != -1) chatDAO.insertar(msgBot);

                agregarBurbuja(inflater, respuesta, true);
                voz.leer(respuesta);
                scrollAlFinal();
            }

            @Override
            public void onError(String error) {
                isLoading = false;
                chatContainer.removeView(typingBubble);
                agregarBurbuja(inflater, error, true);
                scrollAlFinal();
            }
        });
    }

    /**
     * Contesta sin llamar al modelo. El mensaje igual queda en el
     * historial: para la persona fue una conversación, y para el cuidador
     * que después mira el historial también.
     */
    private void responderSinModelo(String texto, String respuesta) {
        suggestedPrompts.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        MensajeChat msgUsuario = new MensajeChat(idUsuario, "usuario", texto);
        historial.add(msgUsuario);
        if (idUsuario != -1) chatDAO.insertar(msgUsuario);
        agregarBurbuja(inflater, texto, false);

        MensajeChat msgBot = new MensajeChat(idUsuario, "bot", respuesta);
        historial.add(msgBot);
        if (idUsuario != -1) chatDAO.insertar(msgBot);
        agregarBurbuja(inflater, respuesta, true);

        voz.leer(respuesta);
        scrollAlFinal();
    }

    /** Los últimos {@link #MENSAJES_DE_CONTEXTO}, o todos si son menos. */
    private java.util.List<MensajeChat> ultimosMensajes() {
        int desde = Math.max(0, historial.size() - MENSAJES_DE_CONTEXTO);
        return new java.util.ArrayList<>(historial.subList(desde, historial.size()));
    }

    // ═══ Voz ═══════════════════════════════════════════════════

    private void alTocarMicrofono() {
        // Si está hablando, el micrófono la calla: alguien que quiere
        // repreguntar no tiene que esperar a que termine la respuesta.
        if (voz != null) voz.callar();

        if (grabador.estaGrabando()) { terminarDeGrabar(); return; }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            empezarAGrabar();
        } else {
            pedirMicrofono.launch(android.Manifest.permission.RECORD_AUDIO);
        }
    }

    private void empezarAGrabar() {
        if (!grabador.empezar(this)) {
            Toast.makeText(this, "No se pudo abrir el micrófono",
                Toast.LENGTH_LONG).show();
            return;
        }
        etMessage.setHint("Te escucho… tocá de nuevo para terminar");
        pintarMicrofono(true);
    }

    private void terminarDeGrabar() {
        pintarMicrofono(false);
        etMessage.setHint("Escribe un mensaje...");

        java.io.File audio = grabador.terminar();
        if (audio == null) {
            Toast.makeText(this, "No llegué a escuchar nada. Probá de nuevo.",
                Toast.LENGTH_SHORT).show();
            return;
        }

        etMessage.setHint("Entendiendo lo que dijiste…");
        openAIClient.transcribir(audio, new OpenAIClient.Callback() {
            @Override public void onRespuesta(String texto) {
                grabador.borrar();
                etMessage.setHint("Escribe un mensaje...");
                // El texto se muestra en el campo en vez de mandarse solo:
                // si la transcripción salió mal, se corrige antes de
                // enviar en lugar de mandar una pregunta equivocada.
                etMessage.setText(texto);
                etMessage.setSelection(texto.length());
            }
            @Override public void onError(String error) {
                grabador.borrar();
                etMessage.setHint("Escribe un mensaje...");
                Toast.makeText(ChatbotActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void pintarMicrofono(boolean grabando) {
        // El ícono, no el contenedor: btn_mic es el FrameLayout que recibe
        // el toque, y castearlo a ImageView reventaba al primer click.
        android.widget.ImageView mic = findViewById(R.id.ic_mic);
        if (mic == null) return;
        mic.setColorFilter(androidx.core.content.ContextCompat.getColor(this,
            grabando ? R.color.danger : R.color.ink_3));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Sale de la pantalla con el micrófono abierto o Vita hablando: las
        // dos cosas tienen que cortarse, no seguir de fondo.
        if (grabador != null) grabador.cancelar();
        if (voz != null) voz.callar();
        pintarMicrofono(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voz != null) voz.liberar();
    }

    private void agregarBurbuja(LayoutInflater inflater, String texto, boolean esVita) {
        int layout = esVita ? R.layout.item_chat_bubble_vita : R.layout.item_chat_bubble_user;
        View burbuja = inflater.inflate(layout, chatContainer, false);
        ((TextView) burbuja.findViewById(R.id.tv_message)).setText(texto);
        chatContainer.addView(burbuja);
    }

    private void scrollAlFinal() {
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void ocultarTeclado() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
    }

    private void confirmarLimpiarHistorial() {
        new AlertDialog.Builder(this)
            .setTitle("Limpiar conversación")
            .setMessage("¿Desea borrar todo el historial de chat con Vita?")
            .setPositiveButton("Limpiar", (d, w) -> {
                if (idUsuario != -1) chatDAO.borrarHistorial(idUsuario);
                historial.clear();
                // Conservar solo el view de sugerencias
                chatContainer.removeAllViews();
                chatContainer.addView(suggestedPrompts);
                suggestedPrompts.setVisibility(View.VISIBLE);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Barra de abajo, delegada en {@link NavInferior} como el resto de las
     * pantallas compartidas.
     *
     * Antes esta era la ÚNICA que la armaba a mano, y por eso se salteaba
     * el modo cuidador: mandaba siempre a MainActivity y a las pantallas
     * del adulto mayor, sin arrastrar el id del paciente. El cuidador
     * entraba a Vita, tocaba "Inicio" y aterrizaba en el home del adulto
     * —vacío, porque es su propia cuenta— con la sensación de que el
     * paciente vinculado había desaparecido.
     */
    private void setupBottomNav() {
        NavInferior.configurar(this, modo, R.id.nav_vita, true);
    }

    /**
     * Las reglas de Vita más la lista de medicamentos de esta persona.
     *
     * Se arma en cada mensaje y no una sola vez porque el estado de la
     * lista cambia: el primer mensaje puede salir mientras la consulta al
     * servidor todavía viaja.
     */
    /** Nombre de quien escribe, para que Vita sepa a quién le habla. */
    private String nombreDelCuidador() {
        String n = sessionManager.getNombre();
        return n == null || n.isEmpty() ? "un familiar" : n.split(" ")[0];
    }

    private String construirSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        // Las reglas viven en PromptVita para que el banco de pruebas
        // corra exactamente las mismas. Ver la nota de esa clase.
        sb.append(PromptVita.REGLAS);

        if (!medicamentosCargados) {
            // Distinto de "no tiene ninguno": acá todavía no sabemos. Se lo
            // decimos así para que no afirme que no tiene nada cargado.
            sb.append("La lista de medicamentos todavía se está cargando. Si "
                + "te preguntan por ella, pedí que prueben de nuevo en un "
                + "momento. No digas que no tiene ninguno.\n");
            return sb.toString();
        }

        if (misMedicamentos.isEmpty()) {
            // Decirlo es mejor que callarlo: sin nada abajo, el modelo
            // asume que la lista no le llegó y se pone a pedírsela a la
            // persona, que es justo lo que la app existe para evitar.
            sb.append("El usuario NO tiene ningún medicamento cargado en la "
                + "app todavía. No se los pidas: ofrecele cargarlos desde la "
                + "pantalla de Medicamentos.\n");
            return sb.toString();
        }

        // Con quién está hablando. Sin esto, abierto por el cuidador, Vita
        // le habla de "tus medicamentos" a alguien que no toma ninguno.
        if (modo.esDeOtro()) {
            sb.append("ATENCIÓN: no estás hablando con el paciente. Estás "
                + "hablando con ").append(nombreDelCuidador()).append(", que "
                + "cuida a ").append(modo.primerNombre().isEmpty()
                    ? "esta persona" : modo.primerNombre())
              .append(". Los medicamentos de abajo son DEL PACIENTE, no de "
                + "quien te escribe. Hablá del paciente en tercera persona: "
                + "\"toma\", \"le toca\", \"su médico\" — nunca \"tomás\" ni "
                + "\"tu médico\". Los mismos límites siguen valiendo: podés "
                + "contar lo que está cargado, no indicar cambios.\n\n");
        }

        sb.append(modo.esDeOtro()
            ? "Medicamentos del paciente:\n"
            : "Medicamentos actuales del usuario:\n");
        for (Medicamento med : misMedicamentos) {
            sb.append("- ").append(med.getNombre());
            if (med.getDosis() > 0) {
                sb.append(" ").append((int) med.getDosis())
                  .append(" ").append(med.getUnidad() != null ? med.getUnidad() : "");
            }
            // Las horas CONCRETAS, no "desde las 17:00 cada 8h". La
            // pregunta más frecuente es "¿a qué hora tomo?", y obligar al
            // modelo a hacer la cuenta es pedirle que se equivoque.
            String horas = horasDe(med);
            if (!horas.isEmpty()) sb.append(", a las ").append(horas);

            String inst = instruccionLegible(med.getInstrucciones());
            if (!inst.isEmpty()) sb.append(" (").append(inst).append(")");

            if (med.getStockActual() > 0) {
                sb.append(", quedan ").append(med.getStockActual());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** "17:00, 01:00 y 09:00" — todas las tomas del día. */
    private String horasDe(Medicamento med) {
        List<Horario> horarios = misHorarios.get(med.getId());
        if (horarios == null || horarios.isEmpty()) return "";

        List<String> todas = new ArrayList<>();
        for (Horario h : horarios) {
            String inicio = h.getHoraInicio();
            if (inicio == null || inicio.length() < 5) continue;

            int intervalo = h.getIntervaloHoras();
            int cantidad = (intervalo > 0 && intervalo <= 24) ? 24 / intervalo : 1;
            try {
                int hora   = Integer.parseInt(inicio.substring(0, 2));
                int minuto = Integer.parseInt(inicio.substring(3, 5));
                for (int i = 0; i < cantidad; i++) {
                    String t = String.format(java.util.Locale.getDefault(),
                        "%02d:%02d", (hora + intervalo * i) % 24, minuto);
                    if (!todas.contains(t)) todas.add(t);
                }
            } catch (NumberFormatException e) {
                todas.add(inicio);
            }
        }
        if (todas.isEmpty()) return "";
        java.util.Collections.sort(todas);

        // "y" antes de la última: el texto se lee en voz alta.
        if (todas.size() == 1) return todas.get(0);
        return android.text.TextUtils.join(", ", todas.subList(0, todas.size() - 1))
            + " y " + todas.get(todas.size() - 1);
    }

    /** Los tags de instrucciones no se le mandan crudos al modelo. */
    private String instruccionLegible(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        switch (tag) {
            case "despues_comer": return "después de comer";
            case "antes_comer":   return "antes de comer";
            case "ayunas":        return "en ayunas";
            case "con_agua":      return "con agua";
            case "con_leche":     return "con leche";
            case "antes_dormir":  return "antes de dormir";
            case "al_despertar":  return "al despertar";
            case "sin_restriccion": return "";
            default: return tag;
        }
    }
}
