-- =============================================================
-- VIMED — Esquema de base de datos para Supabase (PostgreSQL)
-- =============================================================
-- App de gestión de medicación para adultos mayores.
-- Equivalente PostgreSQL del DatabaseHelper.java (SQLite local).
--
-- Todas las tablas se crean dentro del esquema privado `vimed`,
-- NO en `public`. Esto separa los datos de la app de los esquemas
-- por defecto de Supabase (auth, storage, public, etc.).
--
-- Para ejecutar:
--   1. Supabase Dashboard → SQL Editor → New query
--   2. Pegar este archivo completo y ejecutar
--   3. Settings → API → "Exposed schemas": agregar `vimed`
--      (de lo contrario el cliente REST no verá las tablas)
-- =============================================================

-- ── 0. Crear esquema privado ──────────────────────────────────
CREATE SCHEMA IF NOT EXISTS vimed;

-- Que las nuevas consultas resuelvan tablas sin prefijo en esta sesión.
SET search_path TO vimed, public;


-- ── Limpiar (opcional, comentar si ya hay datos) ──────────────
DROP TABLE IF EXISTS vimed.stock_movimientos     CASCADE;
DROP TABLE IF EXISTS vimed.chatbot_historial     CASCADE;
DROP TABLE IF EXISTS vimed.citas_medicas         CASCADE;
DROP TABLE IF EXISTS vimed.interacciones         CASCADE;
DROP TABLE IF EXISTS vimed.notificaciones        CASCADE;
DROP TABLE IF EXISTS vimed.registro_tomas        CASCADE;
DROP TABLE IF EXISTS vimed.horarios              CASCADE;
DROP TABLE IF EXISTS vimed.medicamentos          CASCADE;
DROP TABLE IF EXISTS vimed.vinculacion_familiar  CASCADE;
DROP TABLE IF EXISTS vimed.usuarios              CASCADE;


-- =============================================================
-- 1. USUARIOS
-- =============================================================
CREATE TABLE vimed.usuarios (
    id_usuario       BIGSERIAL PRIMARY KEY,
    nombre           TEXT        NOT NULL,
    correo           TEXT        NOT NULL UNIQUE,
    contrasena       TEXT,                              -- hash en producción
    rol              TEXT        NOT NULL DEFAULT ''
                                 CHECK (rol IN ('', 'adulto_mayor', 'familiar')),
    fecha_registro   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuarios_correo ON vimed.usuarios(correo);


-- =============================================================
-- 2. VINCULACIÓN FAMILIAR
-- =============================================================
CREATE TABLE vimed.vinculacion_familiar (
    id_vinculo     BIGSERIAL PRIMARY KEY,
    id_adulto      BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario) ON DELETE CASCADE,
    id_familiar    BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario) ON DELETE CASCADE,
    estado         TEXT        NOT NULL DEFAULT 'pendiente'
                                CHECK (estado IN ('pendiente', 'aceptado', 'rechazado')),
    fecha_vinculo  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_link   CHECK (id_adulto <> id_familiar),
    CONSTRAINT vinculo_unico  UNIQUE (id_adulto, id_familiar)
);

CREATE INDEX idx_vinc_adulto    ON vimed.vinculacion_familiar(id_adulto);
CREATE INDEX idx_vinc_familiar  ON vimed.vinculacion_familiar(id_familiar);


-- =============================================================
-- 3. MEDICAMENTOS
-- =============================================================
CREATE TABLE vimed.medicamentos (
    id_medicamento  BIGSERIAL PRIMARY KEY,
    id_usuario      BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario) ON DELETE CASCADE,
    nombre          TEXT        NOT NULL,
    presentacion    TEXT,                              -- Comprimido, Cápsula, Jarabe, etc.
    dosis           REAL,
    unidad          TEXT        CHECK (unidad IN ('mg', 'ml', 'mcg', 'g', 'UI') OR unidad IS NULL),
    instrucciones   TEXT,                              -- tag: despues_comer, ayunas, etc.
    color_icono     TEXT,                              -- rojo | azul | verde | etc.
    stock_actual    INTEGER     NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    stock_minimo    INTEGER     NOT NULL DEFAULT 5 CHECK (stock_minimo >= 0),
    activo          BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_med_usuario ON vimed.medicamentos(id_usuario);
CREATE INDEX idx_med_activo  ON vimed.medicamentos(id_usuario, activo);


-- =============================================================
-- 4. HORARIOS
-- =============================================================
CREATE TABLE vimed.horarios (
    id_horario       BIGSERIAL PRIMARY KEY,
    id_medicamento   BIGINT      NOT NULL REFERENCES vimed.medicamentos(id_medicamento) ON DELETE CASCADE,
    hora_inicio      TEXT        NOT NULL,             -- formato "HH:MM"
    intervalo_horas  INTEGER     NOT NULL CHECK (intervalo_horas BETWEEN 1 AND 24),
    personalizado    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_hor_medicamento ON vimed.horarios(id_medicamento);


-- =============================================================
-- 5. REGISTRO DE TOMAS
-- =============================================================
CREATE TABLE vimed.registro_tomas (
    id_registro              BIGSERIAL PRIMARY KEY,
    id_horario               BIGINT      NOT NULL REFERENCES vimed.horarios(id_horario)  ON DELETE CASCADE,
    id_usuario               BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario)  ON DELETE CASCADE,
    fecha_hora_programada    TIMESTAMPTZ NOT NULL,
    fecha_hora_confirmacion  TIMESTAMPTZ,
    estado                   TEXT        NOT NULL DEFAULT 'omitida'
                                          CHECK (estado IN ('confirmada', 'pospuesta', 'omitida'))
);

CREATE INDEX idx_reg_usuario_fecha ON vimed.registro_tomas(id_usuario, fecha_hora_programada);
CREATE INDEX idx_reg_horario       ON vimed.registro_tomas(id_horario);


-- =============================================================
-- 6. NOTIFICACIONES
-- =============================================================
CREATE TABLE vimed.notificaciones (
    id_notificacion  BIGSERIAL PRIMARY KEY,
    id_destinatario  BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario)       ON DELETE CASCADE,
    id_registro      BIGINT      REFERENCES vimed.registro_tomas(id_registro)         ON DELETE SET NULL,
    tipo             TEXT        NOT NULL CHECK (tipo IN ('toma', 'stock', 'interaccion', 'cita')),
    mensaje          TEXT,
    enviada          BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_envio      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_not_destinatario ON vimed.notificaciones(id_destinatario);


-- =============================================================
-- 7. INTERACCIONES ENTRE MEDICAMENTOS
-- =============================================================
CREATE TABLE vimed.interacciones (
    id_interaccion    BIGSERIAL PRIMARY KEY,
    id_medicamento_a  BIGINT NOT NULL REFERENCES vimed.medicamentos(id_medicamento) ON DELETE CASCADE,
    id_medicamento_b  BIGINT NOT NULL REFERENCES vimed.medicamentos(id_medicamento) ON DELETE CASCADE,
    nivel_riesgo      TEXT   NOT NULL CHECK (nivel_riesgo IN ('bajo', 'medio', 'alto')),
    descripcion       TEXT,
    CONSTRAINT med_distintos CHECK (id_medicamento_a <> id_medicamento_b)
);

CREATE INDEX idx_int_med_a ON vimed.interacciones(id_medicamento_a);
CREATE INDEX idx_int_med_b ON vimed.interacciones(id_medicamento_b);


-- =============================================================
-- 8. CITAS MÉDICAS
-- =============================================================
CREATE TABLE vimed.citas_medicas (
    id_cita               BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario) ON DELETE CASCADE,
    medico                TEXT        NOT NULL,
    especialidad          TEXT,
    fecha_hora            TIMESTAMPTZ NOT NULL,
    lugar                 TEXT,
    notas                 TEXT,
    recordatorio_enviado  BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cit_usuario_fecha ON vimed.citas_medicas(id_usuario, fecha_hora);


-- =============================================================
-- 9. HISTORIAL DEL CHATBOT (VITA)
-- =============================================================
CREATE TABLE vimed.chatbot_historial (
    id_mensaje    BIGSERIAL PRIMARY KEY,
    id_usuario    BIGINT      NOT NULL REFERENCES vimed.usuarios(id_usuario) ON DELETE CASCADE,
    rol_mensaje   TEXT        NOT NULL CHECK (rol_mensaje IN ('usuario', 'bot')),
    contenido     TEXT        NOT NULL,
    fecha_hora    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_usuario_fecha ON vimed.chatbot_historial(id_usuario, fecha_hora);


-- =============================================================
-- 10. MOVIMIENTOS DE STOCK (auditoría)
-- =============================================================
CREATE TABLE vimed.stock_movimientos (
    id_movimiento     BIGSERIAL PRIMARY KEY,
    id_medicamento    BIGINT      NOT NULL REFERENCES vimed.medicamentos(id_medicamento) ON DELETE CASCADE,
    cantidad_anterior INTEGER     NOT NULL CHECK (cantidad_anterior >= 0),
    cantidad_nueva    INTEGER     NOT NULL CHECK (cantidad_nueva    >= 0),
    motivo            TEXT        NOT NULL CHECK (motivo IN ('toma', 'ajuste_manual', 'reposicion')),
    fecha             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stk_medicamento ON vimed.stock_movimientos(id_medicamento);


-- =============================================================
-- PERMISOS PARA EXPONER EL ESQUEMA VÍA POSTGREST
-- =============================================================
-- Por defecto Supabase solo expone `public`. Para que las llamadas
-- desde el cliente (PostgREST) lleguen al esquema `vimed` hay que:
--   1) Otorgar USAGE al schema y privilegios a las tablas.
--   2) Agregar `vimed` a "Exposed schemas" en Settings → API.
-- =============================================================

GRANT USAGE ON SCHEMA vimed TO anon, authenticated, service_role;

GRANT ALL ON ALL TABLES    IN SCHEMA vimed TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA vimed TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA vimed TO anon, authenticated, service_role;

-- Lo mismo para futuras tablas/secuencias que se creen en este esquema.
ALTER DEFAULT PRIVILEGES IN SCHEMA vimed
    GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA vimed
    GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA vimed
    GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;


-- =============================================================
-- ROW LEVEL SECURITY (RLS)
-- =============================================================
-- RLS activada en todas las tablas. Política temporal: permitir
-- todo a usuarios autenticados.
-- TODO: cuando se conecte Supabase Auth, reemplazar la política
-- por filtros con `auth.uid()::text = id_usuario::text` (o mapeo).
-- =============================================================

ALTER TABLE vimed.usuarios              ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.vinculacion_familiar  ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.medicamentos          ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.horarios              ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.registro_tomas        ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.notificaciones        ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.interacciones         ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.citas_medicas         ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.chatbot_historial     ENABLE ROW LEVEL SECURITY;
ALTER TABLE vimed.stock_movimientos     ENABLE ROW LEVEL SECURITY;

CREATE POLICY permit_authenticated ON vimed.usuarios             FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.vinculacion_familiar FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.medicamentos         FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.horarios             FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.registro_tomas       FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.notificaciones       FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.interacciones        FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.citas_medicas        FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.chatbot_historial    FOR ALL TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY permit_authenticated ON vimed.stock_movimientos    FOR ALL TO authenticated USING (true) WITH CHECK (true);


-- =============================================================
-- DATOS DE EJEMPLO (opcional — comentar si no se quieren)
-- =============================================================

-- Usuarios de prueba
INSERT INTO vimed.usuarios (nombre, correo, contrasena, rol) VALUES
    ('Rosa Benítez',  'rosa@vimed.test',   'demo1234', 'adulto_mayor'),
    ('Carla Benítez', 'carla@vimed.test',  'demo1234', 'familiar');

-- Vínculo madre-hija
INSERT INTO vimed.vinculacion_familiar (id_adulto, id_familiar, estado) VALUES
    (1, 2, 'aceptado');

-- Medicamentos de Rosa
INSERT INTO vimed.medicamentos (id_usuario, nombre, presentacion, dosis, unidad, instrucciones, color_icono, stock_actual, stock_minimo) VALUES
    (1, 'Metformina',   'Comprimido', 850, 'mg', 'despues_comer',   'azul',     20, 5),
    (1, 'Losartán',     'Comprimido',  50, 'mg', 'al_despertar',    'verde',    15, 5),
    (1, 'Atorvastatina','Comprimido',  20, 'mg', 'antes_dormir',    'amarillo', 30, 5),
    (1, 'Omeprazol',    'Cápsula',     20, 'mg', 'ayunas',          'morado',    8, 5);

-- Horarios
INSERT INTO vimed.horarios (id_medicamento, hora_inicio, intervalo_horas, personalizado) VALUES
    (1, '08:00', 8,  FALSE),   -- Metformina cada 8h
    (2, '08:00', 24, FALSE),   -- Losartán 1x día
    (3, '22:00', 24, FALSE),   -- Atorvastatina noche
    (4, '07:30', 24, FALSE);   -- Omeprazol ayunas

-- Cita médica próxima
INSERT INTO vimed.citas_medicas (id_usuario, medico, especialidad, fecha_hora, lugar, notas) VALUES
    (1, 'Dra. Acosta', 'Cardiología', NOW() + INTERVAL '3 days', 'Sanatorio Migone',
       'Llevar últimos análisis');


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
-- Confirmar que todas las tablas se crearon en el esquema vimed:
SELECT table_schema, table_name
FROM   information_schema.tables
WHERE  table_schema = 'vimed'
ORDER  BY table_name;
