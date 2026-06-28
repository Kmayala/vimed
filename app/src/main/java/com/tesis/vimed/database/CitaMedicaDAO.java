package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.CitaMedica;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CitaMedicaDAO {
    private final DatabaseHelper dbHelper;

    public CitaMedicaDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(CitaMedica cita) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_CIT_USUARIO, cita.getIdUsuario());
        cv.put(DatabaseHelper.COL_CIT_MEDICO, cita.getMedico());
        cv.put(DatabaseHelper.COL_CIT_ESPECIALIDAD, cita.getEspecialidad());
        cv.put(DatabaseHelper.COL_CIT_FECHA, cita.getFechaHora());
        cv.put(DatabaseHelper.COL_CIT_LUGAR, cita.getLugar());
        cv.put(DatabaseHelper.COL_CIT_NOTAS, cita.getNotas());
        cv.put(DatabaseHelper.COL_CIT_RECORDATORIO, 0);
        return db.insert(DatabaseHelper.TABLE_CITAS, null, cv);
    }

    public List<CitaMedica> listarProximas(int idUsuario) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<CitaMedica> lista = new ArrayList<>();
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Cursor c = db.query(DatabaseHelper.TABLE_CITAS, null,
            DatabaseHelper.COL_CIT_USUARIO + "=? AND " + DatabaseHelper.COL_CIT_FECHA + " >= ?",
            new String[]{String.valueOf(idUsuario), hoy},
            null, null, DatabaseHelper.COL_CIT_FECHA + " ASC");
        while (c.moveToNext()) lista.add(cursorToCita(c));
        c.close();
        return lista;
    }

    public List<CitaMedica> listarTodas(int idUsuario) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<CitaMedica> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_CITAS, null,
            DatabaseHelper.COL_CIT_USUARIO + "=?",
            new String[]{String.valueOf(idUsuario)},
            null, null, DatabaseHelper.COL_CIT_FECHA + " DESC");
        while (c.moveToNext()) lista.add(cursorToCita(c));
        c.close();
        return lista;
    }

    public int eliminar(int idCita) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_CITAS,
            DatabaseHelper.COL_CIT_ID + "=?", new String[]{String.valueOf(idCita)});
    }

    private CitaMedica cursorToCita(Cursor c) {
        CitaMedica cita = new CitaMedica();
        cita.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_ID)));
        cita.setIdUsuario(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_USUARIO)));
        cita.setMedico(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_MEDICO)));
        cita.setEspecialidad(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_ESPECIALIDAD)));
        cita.setFechaHora(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_FECHA)));
        cita.setLugar(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_LUGAR)));
        cita.setNotas(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_NOTAS)));
        cita.setRecordatorioEnviado(
            c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CIT_RECORDATORIO)) == 1);
        return cita;
    }
}
