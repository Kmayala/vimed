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

import com.tesis.vimed.api.AnthropicClient;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.MensajeChatDAO;
import com.tesis.vimed.database.MedicamentoDAO;
import com.tesis.vimed.database.HorarioDAO;
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
    private View typingBubble;

    private SessionManager sessionManager;
    private AnthropicClient anthropicClient;
    private MensajeChatDAO chatDAO;
    private List<MensajeChat> historial;
    private int idUsuario;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        sessionManager = new SessionManager(this);
        idUsuario = sessionManager.getIdUsuario();
        anthropicClient = new AnthropicClient();
        chatDAO = new MensajeChatDAO(DatabaseHelper.getInstance(this));

        chatContainer = findViewById(R.id.chat_container);
        scrollChat = findViewById(R.id.scroll_chat);
        etMessage = findViewById(R.id.et_message);
        suggestedPrompts = findViewById(R.id.suggested_prompts);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        setupBottomNav();
        findViewById(R.id.btn_send).setOnClickListener(v -> enviarMensaje());
        findViewById(R.id.btn_mic).setOnClickListener(v ->
            Toast.makeText(this, "Función de voz próximamente", Toast.LENGTH_SHORT).show());
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

        // Llamar a la API
        String systemPrompt = construirSystemPrompt();
        anthropicClient.enviarMensaje(systemPrompt, historial, new AnthropicClient.Callback() {
            @Override
            public void onRespuesta(String respuesta) {
                isLoading = false;
                chatContainer.removeView(typingBubble);

                MensajeChat msgBot = new MensajeChat(idUsuario, "bot", respuesta);
                historial.add(msgBot);
                if (idUsuario != -1) chatDAO.insertar(msgBot);

                agregarBurbuja(inflater, respuesta, true);
                scrollAlFinal();
            }

            @Override
            public void onError(String error) {
                isLoading = false;
                chatContainer.removeView(typingBubble);
                agregarBurbuja(inflater,
                    "Lo siento, no pude conectarme en este momento. Verifique su conexión a internet.",
                    true);
                scrollAlFinal();
            }
        });
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

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_vita);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_vita) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_meds) {
                startActivity(new Intent(this, MedsListActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_appointments) {
                startActivity(new Intent(this, AppointmentsActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(this, DashboardActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    private String construirSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres Vita, un asistente de salud amable y empático especializado en medicación para adultos mayores. ");
        sb.append("Responde siempre en español, de forma clara, sencilla y tranquilizadora. ");
        sb.append("Nunca reemplaces el consejo médico profesional — siempre recomienda consultar al médico para decisiones clínicas importantes. ");
        sb.append("Sé conciso: respuestas de 2-4 oraciones cuando sea posible.\n\n");

        if (idUsuario != -1) {
            try {
                MedicamentoDAO medDAO = new MedicamentoDAO(DatabaseHelper.getInstance(this));
                HorarioDAO horDAO = new HorarioDAO(DatabaseHelper.getInstance(this));
                List<Medicamento> meds = medDAO.listarPorUsuario(idUsuario);
                if (!meds.isEmpty()) {
                    sb.append("Medicamentos actuales del usuario:\n");
                    for (Medicamento med : meds) {
                        sb.append("- ").append(med.getNombre());
                        if (med.getDosis() > 0) {
                            sb.append(" ").append((int) med.getDosis())
                              .append(" ").append(med.getUnidad() != null ? med.getUnidad() : "");
                        }
                        List<Horario> horarios = horDAO.listarPorMedicamento(med.getId());
                        if (!horarios.isEmpty()) {
                            Horario h = horarios.get(0);
                            sb.append(", desde las ").append(h.getHoraInicio())
                              .append(" cada ").append(h.getIntervaloHoras()).append("h");
                        }
                        if (med.getInstrucciones() != null && !med.getInstrucciones().isEmpty()) {
                            sb.append(". Instrucciones: ").append(med.getInstrucciones());
                        }
                        sb.append("\n");
                    }
                }
            } catch (Exception ignored) {}
        }

        return sb.toString();
    }
}
