package com.tesis.vimed.models;

import com.google.gson.annotations.SerializedName;

/**
 * Si los avisos de un vínculo le van a llegar de verdad al cuidador.
 *
 * Estar vinculado no alcanza: el cuidador tiene que haber abierto Vimed
 * en su celular con la sesión iniciada, que es cuando se guarda el token
 * de push. Hasta que eso pase, el paciente marca sus tomas creyendo que
 * del otro lado alguien las ve, y no las ve nadie.
 *
 * Lo devuelve la función mis_vinculos_reciben_avisos(), que contesta un
 * booleano y nunca el token: ver supabase_avisos_llegan.sql.
 */
public class AvisoVinculo {

    @SerializedName("id_vinculo")
    private int idVinculo;

    @SerializedName("id_familiar")
    private int idFamiliar;

    @SerializedName("recibe_avisos")
    private boolean recibeAvisos;

    public int getIdVinculo()      { return idVinculo; }
    public int getIdFamiliar()     { return idFamiliar; }
    public boolean recibeAvisos()  { return recibeAvisos; }
}
