package com.tesis.vimed.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "vimed.db";
    private static final int DATABASE_VERSION = 1;

    // ============================================================
    // TABLA: usuarios
    // ============================================================
    public static final String TABLE_USUARIOS = "usuarios";
    public static final String COL_USR_ID = "id_usuario";
    public static final String COL_USR_NOMBRE = "nombre";
    public static final String COL_USR_CORREO = "correo";
    public static final String COL_USR_CONTRASENA = "contrasena";
    public static final String COL_USR_ROL = "rol"; // adulto_mayor | familiar
    public static final String COL_USR_FECHA = "fecha_registro";

    private static final String CREATE_USUARIOS =
        "CREATE TABLE " + TABLE_USUARIOS + " (" +
        COL_USR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_USR_NOMBRE + " TEXT NOT NULL, " +
        COL_USR_CORREO + " TEXT UNIQUE NOT NULL, " +
        COL_USR_CONTRASENA + " TEXT, " +
        COL_USR_ROL + " TEXT NOT NULL DEFAULT '', " +
        COL_USR_FECHA + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

    // ============================================================
    // TABLA: vinculacion_familiar
    // ============================================================
    public static final String TABLE_VINCULACION = "vinculacion_familiar";
    public static final String COL_VIN_ID = "id_vinculo";
    public static final String COL_VIN_ADULTO = "id_adulto";
    public static final String COL_VIN_FAMILIAR = "id_familiar";
    public static final String COL_VIN_ESTADO = "estado"; // pendiente | aceptado
    public static final String COL_VIN_FECHA = "fecha_vinculo";

    private static final String CREATE_VINCULACION =
        "CREATE TABLE " + TABLE_VINCULACION + " (" +
        COL_VIN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_VIN_ADULTO + " INTEGER NOT NULL, " +
        COL_VIN_FAMILIAR + " INTEGER NOT NULL, " +
        COL_VIN_ESTADO + " TEXT DEFAULT 'pendiente', " +
        COL_VIN_FECHA + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
        "FOREIGN KEY(" + COL_VIN_ADULTO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "), " +
        "FOREIGN KEY(" + COL_VIN_FAMILIAR + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: medicamentos
    // ============================================================
    public static final String TABLE_MEDICAMENTOS = "medicamentos";
    public static final String COL_MED_ID = "id_medicamento";
    public static final String COL_MED_USUARIO = "id_usuario";
    public static final String COL_MED_NOMBRE = "nombre";
    public static final String COL_MED_PRESENTACION = "presentacion";
    public static final String COL_MED_DOSIS = "dosis";
    public static final String COL_MED_UNIDAD = "unidad"; // mg | ml | mcg
    public static final String COL_MED_INSTRUCCIONES = "instrucciones";
    public static final String COL_MED_COLOR = "color_icono";
    public static final String COL_MED_STOCK = "stock_actual";
    public static final String COL_MED_STOCK_MIN = "stock_minimo";
    public static final String COL_MED_ACTIVO = "activo";

    private static final String CREATE_MEDICAMENTOS =
        "CREATE TABLE " + TABLE_MEDICAMENTOS + " (" +
        COL_MED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_MED_USUARIO + " INTEGER NOT NULL, " +
        COL_MED_NOMBRE + " TEXT NOT NULL, " +
        COL_MED_PRESENTACION + " TEXT, " +
        COL_MED_DOSIS + " REAL, " +
        COL_MED_UNIDAD + " TEXT, " +
        COL_MED_INSTRUCCIONES + " TEXT, " +
        COL_MED_COLOR + " TEXT, " +
        COL_MED_STOCK + " INTEGER DEFAULT 0, " +
        COL_MED_STOCK_MIN + " INTEGER DEFAULT 5, " +
        COL_MED_ACTIVO + " INTEGER DEFAULT 1, " +
        "FOREIGN KEY(" + COL_MED_USUARIO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: horarios
    // ============================================================
    public static final String TABLE_HORARIOS = "horarios";
    public static final String COL_HOR_ID = "id_horario";
    public static final String COL_HOR_MEDICAMENTO = "id_medicamento";
    public static final String COL_HOR_HORA_INICIO = "hora_inicio"; // HH:mm
    public static final String COL_HOR_INTERVALO = "intervalo_horas"; // 6 | 8 | 12 | 24
    public static final String COL_HOR_PERSONALIZADO = "personalizado"; // 0 | 1

    private static final String CREATE_HORARIOS =
        "CREATE TABLE " + TABLE_HORARIOS + " (" +
        COL_HOR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_HOR_MEDICAMENTO + " INTEGER NOT NULL, " +
        COL_HOR_HORA_INICIO + " TEXT NOT NULL, " +
        COL_HOR_INTERVALO + " INTEGER, " +
        COL_HOR_PERSONALIZADO + " INTEGER DEFAULT 0, " +
        "FOREIGN KEY(" + COL_HOR_MEDICAMENTO + ") REFERENCES " + TABLE_MEDICAMENTOS + "(" + COL_MED_ID + "))";

    // ============================================================
    // TABLA: registro_tomas
    // ============================================================
    public static final String TABLE_REGISTRO = "registro_tomas";
    public static final String COL_REG_ID = "id_registro";
    public static final String COL_REG_HORARIO = "id_horario";
    public static final String COL_REG_USUARIO = "id_usuario";
    public static final String COL_REG_PROGRAMADA = "fecha_hora_programada";
    public static final String COL_REG_CONFIRMACION = "fecha_hora_confirmacion";
    public static final String COL_REG_ESTADO = "estado"; // confirmada | pospuesta | omitida

    private static final String CREATE_REGISTRO =
        "CREATE TABLE " + TABLE_REGISTRO + " (" +
        COL_REG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_REG_HORARIO + " INTEGER NOT NULL, " +
        COL_REG_USUARIO + " INTEGER NOT NULL, " +
        COL_REG_PROGRAMADA + " DATETIME NOT NULL, " +
        COL_REG_CONFIRMACION + " DATETIME, " +
        COL_REG_ESTADO + " TEXT DEFAULT 'omitida', " +
        "FOREIGN KEY(" + COL_REG_HORARIO + ") REFERENCES " + TABLE_HORARIOS + "(" + COL_HOR_ID + "), " +
        "FOREIGN KEY(" + COL_REG_USUARIO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: notificaciones
    // ============================================================
    public static final String TABLE_NOTIFICACIONES = "notificaciones";
    public static final String COL_NOT_ID = "id_notificacion";
    public static final String COL_NOT_DESTINATARIO = "id_destinatario";
    public static final String COL_NOT_REGISTRO = "id_registro";
    public static final String COL_NOT_TIPO = "tipo"; // toma | stock | interaccion | cita
    public static final String COL_NOT_MENSAJE = "mensaje";
    public static final String COL_NOT_ENVIADA = "enviada";
    public static final String COL_NOT_FECHA = "fecha_envio";

    private static final String CREATE_NOTIFICACIONES =
        "CREATE TABLE " + TABLE_NOTIFICACIONES + " (" +
        COL_NOT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_NOT_DESTINATARIO + " INTEGER NOT NULL, " +
        COL_NOT_REGISTRO + " INTEGER, " +
        COL_NOT_TIPO + " TEXT NOT NULL, " +
        COL_NOT_MENSAJE + " TEXT, " +
        COL_NOT_ENVIADA + " INTEGER DEFAULT 0, " +
        COL_NOT_FECHA + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
        "FOREIGN KEY(" + COL_NOT_DESTINATARIO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: interacciones
    // ============================================================
    public static final String TABLE_INTERACCIONES = "interacciones";
    public static final String COL_INT_ID = "id_interaccion";
    public static final String COL_INT_MED_A = "id_medicamento_a";
    public static final String COL_INT_MED_B = "id_medicamento_b";
    public static final String COL_INT_RIESGO = "nivel_riesgo"; // bajo | medio | alto
    public static final String COL_INT_DESC = "descripcion";

    private static final String CREATE_INTERACCIONES =
        "CREATE TABLE " + TABLE_INTERACCIONES + " (" +
        COL_INT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_INT_MED_A + " INTEGER NOT NULL, " +
        COL_INT_MED_B + " INTEGER NOT NULL, " +
        COL_INT_RIESGO + " TEXT, " +
        COL_INT_DESC + " TEXT, " +
        "FOREIGN KEY(" + COL_INT_MED_A + ") REFERENCES " + TABLE_MEDICAMENTOS + "(" + COL_MED_ID + "), " +
        "FOREIGN KEY(" + COL_INT_MED_B + ") REFERENCES " + TABLE_MEDICAMENTOS + "(" + COL_MED_ID + "))";

    // ============================================================
    // TABLA: citas_medicas
    // ============================================================
    public static final String TABLE_CITAS = "citas_medicas";
    public static final String COL_CIT_ID = "id_cita";
    public static final String COL_CIT_USUARIO = "id_usuario";
    public static final String COL_CIT_MEDICO = "medico";
    public static final String COL_CIT_ESPECIALIDAD = "especialidad";
    public static final String COL_CIT_FECHA = "fecha_hora";
    public static final String COL_CIT_LUGAR = "lugar";
    public static final String COL_CIT_NOTAS = "notas";
    public static final String COL_CIT_RECORDATORIO = "recordatorio_enviado";

    private static final String CREATE_CITAS =
        "CREATE TABLE " + TABLE_CITAS + " (" +
        COL_CIT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_CIT_USUARIO + " INTEGER NOT NULL, " +
        COL_CIT_MEDICO + " TEXT NOT NULL, " +
        COL_CIT_ESPECIALIDAD + " TEXT, " +
        COL_CIT_FECHA + " DATETIME NOT NULL, " +
        COL_CIT_LUGAR + " TEXT, " +
        COL_CIT_NOTAS + " TEXT, " +
        COL_CIT_RECORDATORIO + " INTEGER DEFAULT 0, " +
        "FOREIGN KEY(" + COL_CIT_USUARIO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: chatbot_historial
    // ============================================================
    public static final String TABLE_CHAT = "chatbot_historial";
    public static final String COL_CHAT_ID = "id_mensaje";
    public static final String COL_CHAT_USUARIO = "id_usuario";
    public static final String COL_CHAT_ROL = "rol_mensaje"; // usuario | bot
    public static final String COL_CHAT_CONTENIDO = "contenido";
    public static final String COL_CHAT_FECHA = "fecha_hora";

    private static final String CREATE_CHAT =
        "CREATE TABLE " + TABLE_CHAT + " (" +
        COL_CHAT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_CHAT_USUARIO + " INTEGER NOT NULL, " +
        COL_CHAT_ROL + " TEXT NOT NULL, " +
        COL_CHAT_CONTENIDO + " TEXT NOT NULL, " +
        COL_CHAT_FECHA + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
        "FOREIGN KEY(" + COL_CHAT_USUARIO + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_USR_ID + "))";

    // ============================================================
    // TABLA: stock_movimientos
    // ============================================================
    public static final String TABLE_STOCK = "stock_movimientos";
    public static final String COL_STK_ID = "id_movimiento";
    public static final String COL_STK_MEDICAMENTO = "id_medicamento";
    public static final String COL_STK_ANTERIOR = "cantidad_anterior";
    public static final String COL_STK_NUEVA = "cantidad_nueva";
    public static final String COL_STK_MOTIVO = "motivo"; // toma | ajuste_manual
    public static final String COL_STK_FECHA = "fecha";

    private static final String CREATE_STOCK =
        "CREATE TABLE " + TABLE_STOCK + " (" +
        COL_STK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COL_STK_MEDICAMENTO + " INTEGER NOT NULL, " +
        COL_STK_ANTERIOR + " INTEGER, " +
        COL_STK_NUEVA + " INTEGER, " +
        COL_STK_MOTIVO + " TEXT, " +
        COL_STK_FECHA + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
        "FOREIGN KEY(" + COL_STK_MEDICAMENTO + ") REFERENCES " + TABLE_MEDICAMENTOS + "(" + COL_MED_ID + "))";

    // ============================================================
    // SINGLETON
    // ============================================================
    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ============================================================
    // CREAR TABLAS
    // ============================================================
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USUARIOS);
        db.execSQL(CREATE_VINCULACION);
        db.execSQL(CREATE_MEDICAMENTOS);
        db.execSQL(CREATE_HORARIOS);
        db.execSQL(CREATE_REGISTRO);
        db.execSQL(CREATE_NOTIFICACIONES);
        db.execSQL(CREATE_INTERACCIONES);
        db.execSQL(CREATE_CITAS);
        db.execSQL(CREATE_CHAT);
        db.execSQL(CREATE_STOCK);
    }

    // ============================================================
    // ACTUALIZAR TABLAS
    // ============================================================
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STOCK);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CITAS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTERACCIONES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTIFICACIONES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REGISTRO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HORARIOS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICAMENTOS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VINCULACION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USUARIOS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
}
