package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.Medicamento;

import java.util.ArrayList;
import java.util.List;

public class MedicamentoDAO {
    private final DatabaseHelper dbHelper;

    public MedicamentoDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(Medicamento m) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_MED_USUARIO, m.getIdUsuario());
        cv.put(DatabaseHelper.COL_MED_NOMBRE, m.getNombre());
        cv.put(DatabaseHelper.COL_MED_PRESENTACION, m.getPresentacion());
        cv.put(DatabaseHelper.COL_MED_DOSIS, m.getDosis());
        cv.put(DatabaseHelper.COL_MED_UNIDAD, m.getUnidad());
        cv.put(DatabaseHelper.COL_MED_INSTRUCCIONES, m.getInstrucciones());
        cv.put(DatabaseHelper.COL_MED_COLOR, m.getColorIcono());
        cv.put(DatabaseHelper.COL_MED_STOCK, m.getStockActual());
        cv.put(DatabaseHelper.COL_MED_STOCK_MIN, m.getStockMinimo());
        cv.put(DatabaseHelper.COL_MED_ACTIVO, 1);
        return db.insert(DatabaseHelper.TABLE_MEDICAMENTOS, null, cv);
    }

    public List<Medicamento> listarPorUsuario(int idUsuario) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Medicamento> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_MEDICAMENTOS, null,
            DatabaseHelper.COL_MED_USUARIO + "=? AND " + DatabaseHelper.COL_MED_ACTIVO + "=1",
            new String[]{String.valueOf(idUsuario)}, null, null,
            DatabaseHelper.COL_MED_NOMBRE + " ASC");
        while (c.moveToNext()) lista.add(cursorToMedicamento(c));
        c.close();
        return lista;
    }

    public Medicamento buscarPorId(int idMedicamento) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DatabaseHelper.TABLE_MEDICAMENTOS, null,
            DatabaseHelper.COL_MED_ID + "=?", new String[]{String.valueOf(idMedicamento)},
            null, null, null);
        if (c.moveToFirst()) {
            Medicamento m = cursorToMedicamento(c);
            c.close();
            return m;
        }
        c.close();
        return null;
    }

    public int actualizarStock(int idMedicamento, int nuevoStock) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_MED_STOCK, nuevoStock);
        return db.update(DatabaseHelper.TABLE_MEDICAMENTOS, cv,
            DatabaseHelper.COL_MED_ID + "=?", new String[]{String.valueOf(idMedicamento)});
    }

    // Baja lógica — no se elimina físicamente
    public int eliminar(int idMedicamento) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_MED_ACTIVO, 0);
        return db.update(DatabaseHelper.TABLE_MEDICAMENTOS, cv,
            DatabaseHelper.COL_MED_ID + "=?", new String[]{String.valueOf(idMedicamento)});
    }

    private Medicamento cursorToMedicamento(Cursor c) {
        Medicamento m = new Medicamento();
        m.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_ID)));
        m.setIdUsuario(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_USUARIO)));
        m.setNombre(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_NOMBRE)));
        m.setPresentacion(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_PRESENTACION)));
        m.setDosis(c.getFloat(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_DOSIS)));
        m.setUnidad(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_UNIDAD)));
        m.setInstrucciones(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_INSTRUCCIONES)));
        m.setColorIcono(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_COLOR)));
        m.setStockActual(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_STOCK)));
        m.setStockMinimo(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_STOCK_MIN)));
        m.setActivo(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_MED_ACTIVO)) == 1);
        return m;
    }
}
