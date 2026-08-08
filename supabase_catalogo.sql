-- =============================================================
-- VIMED — Catálogo de medicamentos comunes (Paraguay, adultos mayores)
-- =============================================================
-- Tablas de referencia (NO específicas de un usuario):
--   - public.catalogo_medicamentos      → master list (~38 meds)
--   - public.interacciones_catalogo     → choques conocidos entre ellos
--
-- Diseñado para correrse DESPUÉS de tener las tablas principales
-- en `public`. Es idempotente: usa IF NOT EXISTS y se puede
-- re-ejecutar sin romper datos existentes.
--
-- Cómo correrlo:
--   Supabase Dashboard → SQL Editor → New query → pegar y Run
-- =============================================================


-- ── 1. CATÁLOGO MAESTRO ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.catalogo_medicamentos (
    id_catalogo       BIGSERIAL PRIMARY KEY,
    nombre_comercial  TEXT    NOT NULL,
    principio_activo  TEXT    NOT NULL UNIQUE,
    presentacion      TEXT,
    dosis_comun       REAL,
    unidad            TEXT    CHECK (unidad IN ('mg','ml','mcg','g','UI') OR unidad IS NULL),
    categoria         TEXT    NOT NULL,
    instrucciones     TEXT,
    requiere_receta   BOOLEAN NOT NULL DEFAULT TRUE,
    activo            BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_cat_nombre     ON public.catalogo_medicamentos(nombre_comercial);
CREATE INDEX IF NOT EXISTS idx_cat_principio  ON public.catalogo_medicamentos(principio_activo);
CREATE INDEX IF NOT EXISTS idx_cat_categoria  ON public.catalogo_medicamentos(categoria);


-- ── 2. FK OPCIONAL DESDE MEDICAMENTOS DE USUARIO AL CATÁLOGO ──
-- Permite que cuando el adulto mayor elija un med del catálogo,
-- guardemos cuál era. Habilita cruzar interacciones automáticamente.
ALTER TABLE public.medicamentos
    ADD COLUMN IF NOT EXISTS id_catalogo BIGINT
    REFERENCES public.catalogo_medicamentos(id_catalogo) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_med_catalogo ON public.medicamentos(id_catalogo);


-- ── 3. INTERACCIONES GENÉRICAS DEL CATÁLOGO ──────────────────
CREATE TABLE IF NOT EXISTS public.interacciones_catalogo (
    id_interaccion_cat BIGSERIAL PRIMARY KEY,
    id_catalogo_a      BIGINT NOT NULL REFERENCES public.catalogo_medicamentos(id_catalogo) ON DELETE CASCADE,
    id_catalogo_b      BIGINT NOT NULL REFERENCES public.catalogo_medicamentos(id_catalogo) ON DELETE CASCADE,
    nivel_riesgo       TEXT   NOT NULL CHECK (nivel_riesgo IN ('bajo','medio','alto')),
    descripcion        TEXT,
    CONSTRAINT distintos_cat       CHECK (id_catalogo_a <> id_catalogo_b),
    CONSTRAINT interaccion_unica   UNIQUE (id_catalogo_a, id_catalogo_b)
);

CREATE INDEX IF NOT EXISTS idx_int_cat_a ON public.interacciones_catalogo(id_catalogo_a);
CREATE INDEX IF NOT EXISTS idx_int_cat_b ON public.interacciones_catalogo(id_catalogo_b);


-- ── 4. RLS: lectura libre del catálogo ───────────────────────
-- El catálogo es referencia pública, no datos sensibles del usuario.
ALTER TABLE public.catalogo_medicamentos   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.interacciones_catalogo  ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS catalogo_lectura  ON public.catalogo_medicamentos;
DROP POLICY IF EXISTS interacc_lectura  ON public.interacciones_catalogo;

CREATE POLICY catalogo_lectura  ON public.catalogo_medicamentos
    FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY interacc_lectura  ON public.interacciones_catalogo
    FOR SELECT TO anon, authenticated USING (true);


-- =============================================================
-- DATOS — Catálogo de medicamentos comunes
-- =============================================================
-- ON CONFLICT por principio_activo → se puede re-ejecutar sin duplicar.

INSERT INTO public.catalogo_medicamentos
    (nombre_comercial, principio_activo, presentacion, dosis_comun, unidad, categoria, instrucciones, requiere_receta)
VALUES
    -- ── Cardiovascular: hipertensión / IECA-ARA II ──
    ('Enalapril 10',     'Enalapril',         'Comprimido', 10,  'mg',  'cardiovascular', 'al_despertar',   TRUE),
    ('Losartán 50',      'Losartán',          'Comprimido', 50,  'mg',  'cardiovascular', 'al_despertar',   TRUE),
    ('Amlodipina 5',     'Amlodipina',        'Comprimido', 5,   'mg',  'cardiovascular', 'sin_restriccion',TRUE),
    ('Atenolol 50',      'Atenolol',          'Comprimido', 50,  'mg',  'cardiovascular', 'sin_restriccion',TRUE),
    ('Carvedilol 25',    'Carvedilol',        'Comprimido', 25,  'mg',  'cardiovascular', 'con_comida',     TRUE),

    -- ── Cardiovascular: diuréticos ──
    ('Hidroclorotiazida 25', 'Hidroclorotiazida', 'Comprimido', 25, 'mg', 'cardiovascular', 'al_despertar', TRUE),
    ('Furosemida 40',    'Furosemida',        'Comprimido', 40,  'mg',  'cardiovascular', 'al_despertar',   TRUE),

    -- ── Cardiovascular: estatinas y antiagregantes ──
    ('Atorvastatina 20', 'Atorvastatina',     'Comprimido', 20,  'mg',  'cardiovascular', 'antes_dormir',   TRUE),
    ('Simvastatina 20',  'Simvastatina',      'Comprimido', 20,  'mg',  'cardiovascular', 'antes_dormir',   TRUE),
    ('Aspirineta 100',   'Ácido acetilsalicílico','Comprimido', 100,'mg','cardiovascular','despues_comer',  FALSE),
    ('Clopidogrel 75',   'Clopidogrel',       'Comprimido', 75,  'mg',  'cardiovascular', 'sin_restriccion',TRUE),
    ('Warfarina 5',      'Warfarina',         'Comprimido', 5,   'mg',  'cardiovascular', 'sin_restriccion',TRUE),

    -- ── Diabetes ──
    ('Metformina 850',   'Metformina',        'Comprimido', 850, 'mg',  'diabetes',       'despues_comer',  TRUE),
    ('Glibenclamida 5',  'Glibenclamida',     'Comprimido', 5,   'mg',  'diabetes',       'antes_comer',    TRUE),
    ('Insulina NPH',     'Insulina NPH',      'Inyectable', 100, 'UI',  'diabetes',       'sin_restriccion',TRUE),

    -- ── Gastrointestinales ──
    ('Omeprazol 20',     'Omeprazol',         'Cápsula',    20,  'mg',  'gastro',         'ayunas',         FALSE),
    ('Pantoprazol 40',   'Pantoprazol',       'Comprimido', 40,  'mg',  'gastro',         'ayunas',         TRUE),
    ('Ranitidina 150',   'Ranitidina',        'Comprimido', 150, 'mg',  'gastro',         'antes_comer',    FALSE),

    -- ── Analgésicos / antiinflamatorios ──
    ('Paracetamol 500',  'Paracetamol',       'Comprimido', 500, 'mg',  'analgesico',     'sin_restriccion',FALSE),
    ('Ibuprofeno 400',   'Ibuprofeno',        'Comprimido', 400, 'mg',  'analgesico',     'despues_comer',  FALSE),
    ('Diclofenac 50',    'Diclofenac',        'Comprimido', 50,  'mg',  'analgesico',     'despues_comer',  FALSE),
    ('Tramadol 50',      'Tramadol',          'Comprimido', 50,  'mg',  'analgesico',     'sin_restriccion',TRUE),

    -- ── Tiroides ──
    ('Levotiroxina 100', 'Levotiroxina',      'Comprimido', 100, 'mcg', 'tiroides',       'ayunas',         TRUE),

    -- ── Próstata ──
    ('Tamsulosina 0.4',  'Tamsulosina',       'Cápsula',    0.4, 'mg',  'urologico',      'despues_comer',  TRUE),

    -- ── Huesos / suplementos ──
    ('Carbonato Calcio 600', 'Carbonato de calcio','Comprimido', 600,'mg','suplemento',  'con_comida',     FALSE),
    ('Vitamina D3 1000', 'Colecalciferol',    'Comprimido', 1000,'UI', 'suplemento',     'con_comida',     FALSE),
    ('Alendronato 70',   'Alendronato',       'Comprimido', 70,  'mg',  'huesos',         'ayunas',         TRUE),

    -- ── Sueño / ansiedad (uso cuidadoso en AM) ──
    ('Alprazolam 0.5',   'Alprazolam',        'Comprimido', 0.5, 'mg',  'ansiolitico',    'antes_dormir',   TRUE),
    ('Clonazepam 2',     'Clonazepam',        'Comprimido', 2,   'mg',  'ansiolitico',    'antes_dormir',   TRUE),
    ('Zolpidem 10',      'Zolpidem',          'Comprimido', 10,  'mg',  'ansiolitico',    'antes_dormir',   TRUE),

    -- ── Respiratorio ──
    ('Salbutamol Inh',   'Salbutamol',        'Inhalador',  100, 'mcg', 'respiratorio',   'sin_restriccion',FALSE),
    ('Budesonida Inh',   'Budesonida',        'Inhalador',  200, 'mcg', 'respiratorio',   'sin_restriccion',TRUE),

    -- ── Antialérgicos ──
    ('Loratadina 10',    'Loratadina',        'Comprimido', 10,  'mg',  'antialergico',   'sin_restriccion',FALSE),
    ('Cetirizina 10',    'Cetirizina',        'Comprimido', 10,  'mg',  'antialergico',   'antes_dormir',   FALSE),

    -- ── Suplementos / hematológicos ──
    ('Sulfato Ferroso',  'Sulfato ferroso',   'Comprimido', 200, 'mg',  'suplemento',     'ayunas',         FALSE),
    ('Complejo B',       'Complejo B',        'Comprimido', 1,   'g',   'suplemento',     'con_comida',     FALSE),

    -- ── Neurológicos / Alzheimer ──
    ('Memantina 10',     'Memantina',         'Comprimido', 10,  'mg',  'neurologico',    'sin_restriccion',TRUE),
    ('Donepezilo 5',     'Donepezilo',        'Comprimido', 5,   'mg',  'neurologico',    'antes_dormir',   TRUE)
ON CONFLICT (principio_activo) DO NOTHING;


-- =============================================================
-- DATOS — Interacciones conocidas entre los meds del catálogo
-- =============================================================
-- Helper: agarra dos meds por principio_activo. Si no existen, no inserta.

INSERT INTO public.interacciones_catalogo (id_catalogo_a, id_catalogo_b, nivel_riesgo, descripcion)
SELECT a.id_catalogo, b.id_catalogo, nivel, descripcion
FROM public.catalogo_medicamentos a
JOIN public.catalogo_medicamentos b ON TRUE
JOIN (VALUES
    -- ── ALTO RIESGO ──
    ('Warfarina',          'Ácido acetilsalicílico',
        'alto',  'Riesgo grave de sangrado. Evitar combinación o controlar INR estrechamente.'),
    ('Warfarina',          'Clopidogrel',
        'alto',  'Sangrado severo. Combinación generalmente contraindicada.'),
    ('Warfarina',          'Ibuprofeno',
        'alto',  'AINE potencia anticoagulante y daña mucosa gástrica. Riesgo de hemorragia digestiva.'),
    ('Warfarina',          'Diclofenac',
        'alto',  'AINE aumenta riesgo de sangrado. Preferir Paracetamol.'),
    ('Ácido acetilsalicílico','Clopidogrel',
        'alto',  'Doble antiagregación. Solo bajo indicación médica estricta en AM.'),
    ('Tramadol',           'Alprazolam',
        'alto',  'Depresión respiratoria y sedación severa. Riesgo de caídas.'),
    ('Tramadol',           'Clonazepam',
        'alto',  'Depresión respiratoria y del SNC. Evitar.'),
    ('Tramadol',           'Zolpidem',
        'alto',  'Sedación profunda. Riesgo de confusión y caídas.'),
    ('Enalapril',          'Losartán',
        'alto',  'Bloqueo doble del SRAA: hiperpotasemia y daño renal agudo. No combinar.'),
    ('Glibenclamida',      'Ácido acetilsalicílico',
        'alto',  'Riesgo de hipoglucemia severa. Monitorear glucemia.'),
    ('Alprazolam',         'Clonazepam',
        'alto',  'Dos benzodiacepinas. Sedación excesiva. Riesgo de caídas y delirio.'),

    -- ── RIESGO MEDIO ──
    ('Ibuprofeno',         'Enalapril',
        'medio', 'AINE reduce efecto antihipertensivo y puede dañar riñón. Vigilar presión y función renal.'),
    ('Ibuprofeno',         'Losartán',
        'medio', 'AINE reduce efecto del ARA II. Daño renal. Limitar uso.'),
    ('Diclofenac',         'Enalapril',
        'medio', 'AINE reduce efecto antihipertensivo y daña riñón.'),
    ('Ibuprofeno',         'Furosemida',
        'medio', 'AINE reduce efecto diurético. Riesgo renal.'),
    ('Diclofenac',         'Hidroclorotiazida',
        'medio', 'Reduce efecto diurético y antihipertensivo.'),
    ('Omeprazol',          'Clopidogrel',
        'medio', 'Omeprazol reduce activación de clopidogrel. Preferir Pantoprazol si se requiere IBP.'),
    ('Atorvastatina',      'Amlodipina',
        'medio', 'Amlodipina aumenta niveles de atorvastatina. No exceder 20 mg de atorvastatina.'),
    ('Levotiroxina',       'Carbonato de calcio',
        'medio', 'Calcio reduce absorción de levotiroxina. Separar 4 horas.'),
    ('Levotiroxina',       'Sulfato ferroso',
        'medio', 'Hierro reduce absorción. Separar 4 horas.'),
    ('Alendronato',        'Carbonato de calcio',
        'medio', 'Calcio bloquea absorción de alendronato. Tomar alendronato 30 min antes, en ayunas.'),
    ('Glibenclamida',      'Atenolol',
        'medio', 'Betabloqueante enmascara síntomas de hipoglucemia. Monitorear glucemia.'),
    ('Salbutamol',         'Atenolol',
        'medio', 'Atenolol antagoniza broncodilatación. Evitar en pacientes con asma/EPOC.'),
    ('Furosemida',         'Enalapril',
        'medio', 'Hipotensión sintomática al iniciar IECA con diurético activo.'),
    ('Furosemida',         'Losartán',
        'medio', 'Hipotensión sintomática inicial. Ajustar dosis.'),
    ('Loratadina',         'Alprazolam',
        'medio', 'Aumenta sedación. Cuidado con caídas en AM.'),
    ('Cetirizina',         'Clonazepam',
        'medio', 'Aumenta sedación.'),
    ('Hidroclorotiazida',  'Glibenclamida',
        'medio', 'Tiazida puede elevar glucemia y reducir efecto antidiabético.'),

    -- ── RIESGO BAJO (informativo, sin alarma) ──
    ('Omeprazol',          'Alprazolam',
        'bajo',  'Omeprazol puede aumentar levemente concentración de alprazolam.'),
    ('Loratadina',         'Cetirizina',
        'bajo',  'Ambos antihistamínicos. No combinar habitualmente; usar uno solo.'),
    ('Paracetamol',        'Warfarina',
        'bajo',  'A dosis altas y prolongadas puede potenciar anticoagulación. Uso ocasional es seguro.'),
    ('Pantoprazol',        'Clopidogrel',
        'bajo',  'Interacción mínima — alternativa segura al omeprazol.')
) AS x(med_a, med_b, nivel, descripcion)
  ON a.principio_activo = x.med_a AND b.principio_activo = x.med_b
ON CONFLICT (id_catalogo_a, id_catalogo_b) DO NOTHING;


-- =============================================================
-- VERIFICACIÓN
-- =============================================================
SELECT 'Catálogo: ' || COUNT(*)::text || ' meds' AS resumen FROM public.catalogo_medicamentos
UNION ALL
SELECT 'Interacciones: ' || COUNT(*)::text       FROM public.interacciones_catalogo;
