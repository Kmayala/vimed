-- =============================================================
-- VIMED — Plantillas para verificar y citar cada interacción
--
-- Una por fila. NINGUNA trae fuente prellenada ni verificado=TRUE:
-- el trabajo es abrir la referencia, confirmar que dice lo mismo, y
-- recién ahí completar. Una fila marcada como verificada con una
-- cita que no corresponde es peor que una sin verificar — la
-- segunda se nota, la primera parece respaldada y nadie la vuelve a
-- mirar.
--
-- LAS DESCRIPCIONES VIENEN REESCRITAS. Las originales estaban en
-- imperativo: "No combinar", "Evitar", "Preferir Paracetamol". Eso
-- es una indicación médica, que es justo lo que el resto de la app
-- —y el prompt de Vita— tiene prohibido dar. Alguien a quien su
-- cardiólogo le recetó enalapril y losartán juntos lee "No
-- combinar" y puede dejar uno. Las nuevas dicen el riesgo y mandan
-- a preguntar.
--
-- CÓMO USARLO. Descomentá el bloque que estás verificando,
-- completá fuente, y corré solo ese. No corras el archivo entero.
--
-- ORDEN SUGERIDO: las once de riesgo ALTO primero. Son las que
-- disparan la advertencia más fuerte y las que más daño hacen si
-- están mal. Con esas citadas ya tenés algo defendible.
-- =============================================================


-- ═════════════════ RIESGO ALTO (11) ═════════════════
-- Ocho de estas están en la tabla de interacciones fármaco-fármaco
-- de los criterios de Beers, que es la fuente que más te conviene:
-- es específica de adultos mayores.

-- 13 — Warfarina + Ácido acetilsalicílico
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos aumentan mucho el riesgo de sangrado. Si tomás los dos, consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 13;

-- 16 — Warfarina + Clopidogrel
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos aumentan mucho el riesgo de sangrado. Esta combinación casi nunca se indica: consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 16;

-- 20 — Warfarina + Ibuprofeno
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El ibuprofeno aumenta el riesgo de sangrado y puede irritar el estómago. Preguntale a tu médico antes de tomarlos juntos.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 20;

-- 21 — Warfarina + Diclofenac
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El diclofenac aumenta el riesgo de sangrado. Preguntale a tu médico qué analgésico te conviene.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 21;

-- 17 — Ácido acetilsalicílico + Clopidogrel
-- OJO: la doble antiagregación es un tratamiento habitual después
-- de un stent. No es un error: es algo que el médico indica.
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Los dos reducen la coagulación y juntos aumentan el riesgo de sangrado. Se usan juntos en algunos tratamientos, siempre indicados por el médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 17;

-- 25 — Tramadol + Alprazolam
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos pueden dar mucho sueño y dificultar la respiración, y aumentan el riesgo de caídas. Consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 25;

-- 29 — Tramadol + Clonazepam
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos pueden dar mucho sueño y dificultar la respiración, y aumentan el riesgo de caídas. Consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 29;

-- 30 — Tramadol + Zolpidem
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos pueden dar mucha somnolencia y confusión, con más riesgo de caídas durante la noche. Consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 30;

-- 28 — Alprazolam + Clonazepam
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Son dos calmantes del mismo tipo. Juntos pueden dar somnolencia y confusión, y aumentan el riesgo de caídas. Consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 28;

-- 6 — Enalapril + Losartán
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Combinados pueden subir el potasio y afectar el riñón. Consultá con tu médico si te indicó los dos.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 6;

-- 12 — Glibenclamida + Ácido acetilsalicílico
-- REVISAR EL NIVEL: el efecto sobre la glucemia es relevante a dosis
-- analgésicas de AAS. A los 100 mg que se usan como antiagregante
-- es mucho menor, y 'alto' puede estar sobredimensionado.
-- UPDATE public.interacciones_catalogo SET
--   nivel_riesgo = 'medio',
--   descripcion = 'Pueden bajar más el azúcar en sangre. Controlá tu glucemia y comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 12;


-- ═════════════════ RIESGO MEDIO (17) ═════════════════

-- 1 — Diclofenac + Enalapril
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El diclofenac puede restarle efecto al enalapril y cargar el riñón. Si lo tomás seguido, comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 1;

-- 2 — Ibuprofeno + Enalapril
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El ibuprofeno puede restarle efecto al enalapril y cargar el riñón. Si lo tomás seguido, comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 2;

-- 4 — Ibuprofeno + Losartán
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El ibuprofeno puede restarle efecto al losartán y cargar el riñón. Si lo tomás seguido, comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 4;

-- 10 — Diclofenac + Hidroclorotiazida
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El diclofenac puede restarle efecto al diurético y a la baja de presión. Comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 10;

-- 11 — Ibuprofeno + Furosemida
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El ibuprofeno puede restarle efecto a la furosemida y cargar el riñón. Comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 11;

-- 3 — Furosemida + Enalapril
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Al empezar, la presión puede bajar de más y dar mareo al pararse. Avisale a tu médico si te pasa.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 3;

-- 5 — Furosemida + Losartán
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Al empezar, la presión puede bajar de más y dar mareo al pararse. Avisale a tu médico si te pasa.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 5;

-- 9 — Glibenclamida + Atenolol
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El atenolol puede tapar los avisos de que el azúcar bajó, como los temblores. Controlá tu glucemia más seguido.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 9;

-- 19 — Hidroclorotiazida + Glibenclamida
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'La hidroclorotiazida puede subir el azúcar en sangre y restarle efecto al antidiabético. Controlá tu glucemia.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 19;

-- 8 — Salbutamol + Atenolol
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El atenolol puede restarle efecto al salbutamol para abrir los bronquios. Importante si tenés asma o EPOC: consultá con tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 8;

-- 15 — Omeprazol + Clopidogrel
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El omeprazol puede restarle efecto al clopidogrel. El pantoprazol no tiene ese problema: preguntale a tu médico si te conviene cambiarlo.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 15;

-- 7 — Atorvastatina + Amlodipina
-- REVISAR: el tope de 20 mg con amlodipina es la regla de la
-- SIMVASTATINA. Confirmá qué dice la ficha de la atorvastatina
-- antes de dejar ese número.
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'La amlodipina puede aumentar el nivel de atorvastatina en sangre. Tu médico puede querer ajustarte la dosis.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 7;

-- 22 — Alendronato + Carbonato de calcio
-- La ficha técnica da el tiempo exacto: confirmalo antes de dejarlo.
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El calcio impide que el alendronato se absorba. El alendronato se toma en ayunas, al menos 30 minutos antes de cualquier otra cosa.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 22;

-- 23 — Levotiroxina + Carbonato de calcio
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El calcio reduce la absorción de la levotiroxina. Conviene separarlos unas 4 horas.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 23;

-- 32 — Levotiroxina + Sulfato ferroso
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El hierro reduce la absorción de la levotiroxina. Conviene separarlos unas 4 horas.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 32;

-- 27 — Cetirizina + Clonazepam
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Juntos pueden dar más sueño de lo habitual, con más riesgo de caídas. Tenelo en cuenta si manejás o te levantás de noche.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 27;

-- 24 — Loratadina + Alprazolam
-- REVISAR EL NIVEL: la loratadina es de segunda generación y casi
-- no da sueño. Es dudoso que esto llegue a 'medio'.
-- UPDATE public.interacciones_catalogo SET
--   nivel_riesgo = 'bajo',
--   descripcion = 'Juntos pueden dar algo más de sueño. Tenelo en cuenta si manejás.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 24;


-- ═════════════════ RIESGO BAJO (4) ═════════════════

-- 18 — Paracetamol + Warfarina
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Tomado seguido y en dosis altas, el paracetamol puede potenciar a la warfarina. De vez en cuando no suele traer problema.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 18;

-- 26 — Omeprazol + Alprazolam
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'El omeprazol puede aumentar levemente el efecto del alprazolam. Si notás más sueño, comentáselo a tu médico.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 26;

-- 31 — Loratadina + Cetirizina
-- No es una interacción sino una duplicación: dos antihistamínicos
-- para lo mismo. Verificá si vale la pena que siga en esta tabla.
-- UPDATE public.interacciones_catalogo SET
--   descripcion = 'Son dos antihistamínicos que hacen lo mismo. Tomar los dos no mejora el efecto. Consultá cuál te conviene.',
--   fuente = '', verificado = TRUE, verificado_el = CURRENT_DATE
-- WHERE id_interaccion_cat = 31;

-- 14 — Pantoprazol + Clopidogrel
-- ESTA NO ES UNA INTERACCIÓN. El propio texto dice que es la
-- alternativa segura. Tal como está, la app muestra una advertencia
-- por una combinación que ella misma declara buena. El dato
-- pertenece a la descripción del par 15 (omeprazol + clopidogrel),
-- donde ya está.
--
--   DELETE FROM public.interacciones_catalogo WHERE id_interaccion_cat = 14;


-- ── CUÁNTO FALTA ─────────────────────────────────────────────
SELECT
    COUNT(*)                                       AS total,
    COUNT(*) FILTER (WHERE verificado)             AS verificadas,
    COUNT(*) FILTER (WHERE NOT verificado)         AS pendientes,
    COUNT(*) FILTER (WHERE NOT verificado
                       AND nivel_riesgo = 'alto')  AS pendientes_riesgo_alto
FROM public.interacciones_catalogo;
