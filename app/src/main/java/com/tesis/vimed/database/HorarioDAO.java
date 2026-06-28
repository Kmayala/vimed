package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.Horario;

import java.util.ArrayList;
import java.util.List;

public class HorarioDAO {
    private final DatabaseHelper dbHelper;

    public HorarioDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(Horario h) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_HOR_MEDICAMENTO, h.getIdMedicamento());
        cv.put(DatabaseHelper.COL_HOR_HORA_INICIO, h.getHoraInicio());
        cv.put(DatabaseHelper.COL_HOR_INTERVALO, h.getIntervaloHoras());
        cv.put(DatabaseHelper.COL_HOR_PERSONALIZADO, h.isPersonalizado() ? 1 : 0);
        return db.insert(DatabaseHelper.TABLE_HORARIOS, null, cv);
    }

    public List<Horario> listarPorMedicamento(int idMedicamento) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Horario> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_HORARIOS, null,
            DatabaseHelper.COL_HOR_MEDICAMENTO + "=?",
            new String[]{String.valueOf(idMedicamento)}, null, null, null);
        while (c.moveToNext()) lista.add(cursorToHorario(c));
        c.close();
        return lista;
    }

    public int eliminarPorMedicamento(int idMedicamento) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_HORARIOS,
            DatabaseHelper.COL_HOR_MEDICAMENTO + "=?",
            new String[]{String.valueOf(idMedicamento)});
    }

    private Horario cursorToHorario(Cursor c) {
        Horario h = new Horario();
        h.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_HOR_ID)));
        h.setIdMedicamento(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_HOR_MEDICAMENTO)));
        h.setHoraInicio(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_HOR_HORA_INICIO)));
        h.setIntervaloHoras(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_HOR_INTERVALO)));
        h.setPersonalizado(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_HOR_PERSONALIZADO)) == 1);
        return h;
    }
}
