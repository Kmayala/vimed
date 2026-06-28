package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class VinculacionDAO {

    private final DatabaseHelper dbHelper;

    public VinculacionDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /** Crea un vínculo entre adulto mayor y familiar. */
    public long insertar(int idAdulto, int idFamiliar) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_VIN_ADULTO, idAdulto);
        cv.put(DatabaseHelper.COL_VIN_FAMILIAR, idFamiliar);
        cv.put(DatabaseHelper.COL_VIN_ESTADO, "aceptado");
        return db.insert(DatabaseHelper.TABLE_VINCULACION, null, cv);
    }

    /** Verifica si ya existe un vínculo entre los dos usuarios. */
    public boolean existeVinculo(int idAdulto, int idFamiliar) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DatabaseHelper.TABLE_VINCULACION, new String[]{DatabaseHelper.COL_VIN_ID},
            DatabaseHelper.COL_VIN_ADULTO + "=? AND " + DatabaseHelper.COL_VIN_FAMILIAR + "=?",
            new String[]{String.valueOf(idAdulto), String.valueOf(idFamiliar)},
            null, null, null);
        boolean existe = c.moveToFirst();
        c.close();
        return existe;
    }

    /**
     * Devuelve pares [idVinculo, idFamiliar] para un adulto mayor dado.
     * Cada elemento del array: índice 0 = id_vinculo, índice 1 = id_familiar.
     */
    public List<int[]> listarFamiliares(int idAdulto) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<int[]> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_VINCULACION,
            new String[]{DatabaseHelper.COL_VIN_ID, DatabaseHelper.COL_VIN_FAMILIAR},
            DatabaseHelper.COL_VIN_ADULTO + "=?",
            new String[]{String.valueOf(idAdulto)},
            null, null, DatabaseHelper.COL_VIN_FECHA + " ASC");
        while (c.moveToNext()) {
            lista.add(new int[]{
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_VIN_ID)),
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_VIN_FAMILIAR))
            });
        }
        c.close();
        return lista;
    }

    /** Elimina un vínculo por su id. */
    public int eliminar(int idVinculo) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_VINCULACION,
            DatabaseHelper.COL_VIN_ID + "=?", new String[]{String.valueOf(idVinculo)});
    }
}
