package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.RegistroToma;

import java.util.ArrayList;
import java.util.List;

public class RegistroTomaDAO {
    private final DatabaseHelper dbHelper;

    public RegistroTomaDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(RegistroToma r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_REG_HORARIO, r.getIdHorario());
        cv.put(DatabaseHelper.COL_REG_USUARIO, r.getIdUsuario());
        cv.put(DatabaseHelper.COL_REG_PROGRAMADA, r.getFechaHoraProgramada());
        cv.put(DatabaseHelper.COL_REG_ESTADO, r.getEstado());
        return db.insert(DatabaseHelper.TABLE_REGISTRO, null, cv);
    }

    public int confirmarToma(int idRegistro, String fechaConfirmacion) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_REG_ESTADO, "confirmada");
        cv.put(DatabaseHelper.COL_REG_CONFIRMACION, fechaConfirmacion);
        return db.update(DatabaseHelper.TABLE_REGISTRO, cv,
            DatabaseHelper.COL_REG_ID + "=?", new String[]{String.valueOf(idRegistro)});
    }

    /**
     * Cambia el estado de un registro. Si pasa a "confirmada" guarda el
     * timestamp; en cualquier otro estado lo limpia (deshacer una confirmación).
     */
    public int actualizarEstado(int idRegistro, String estado, String fechaConfirmacion) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_REG_ESTADO, estado);
        if ("confirmada".equals(estado)) {
            cv.put(DatabaseHelper.COL_REG_CONFIRMACION, fechaConfirmacion);
        } else {
            cv.putNull(DatabaseHelper.COL_REG_CONFIRMACION);
        }
        return db.update(DatabaseHelper.TABLE_REGISTRO, cv,
            DatabaseHelper.COL_REG_ID + "=?", new String[]{String.valueOf(idRegistro)});
    }

    public int posponerToma(int idRegistro) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_REG_ESTADO, "pospuesta");
        return db.update(DatabaseHelper.TABLE_REGISTRO, cv,
            DatabaseHelper.COL_REG_ID + "=?", new String[]{String.valueOf(idRegistro)});
    }

    public List<RegistroToma> listarPorFecha(int idUsuario, String fecha) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<RegistroToma> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_REGISTRO, null,
            DatabaseHelper.COL_REG_USUARIO + "=? AND " +
            DatabaseHelper.COL_REG_PROGRAMADA + " LIKE ?",
            new String[]{String.valueOf(idUsuario), fecha + "%"},
            null, null, DatabaseHelper.COL_REG_PROGRAMADA + " ASC");
        while (c.moveToNext()) lista.add(cursorToRegistro(c));
        c.close();
        return lista;
    }

    public float calcularAdherenciaMes(int idUsuario, String mesAnio) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor total = db.rawQuery(
            "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_REGISTRO +
            " WHERE " + DatabaseHelper.COL_REG_USUARIO + "=? AND " +
            DatabaseHelper.COL_REG_PROGRAMADA + " LIKE ?",
            new String[]{String.valueOf(idUsuario), mesAnio + "%"});
        Cursor confirmadas = db.rawQuery(
            "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_REGISTRO +
            " WHERE " + DatabaseHelper.COL_REG_USUARIO + "=? AND " +
            DatabaseHelper.COL_REG_PROGRAMADA + " LIKE ? AND " +
            DatabaseHelper.COL_REG_ESTADO + "='confirmada'",
            new String[]{String.valueOf(idUsuario), mesAnio + "%"});
        float result = 0;
        if (total.moveToFirst() && confirmadas.moveToFirst()) {
            int t = total.getInt(0);
            int c2 = confirmadas.getInt(0);
            if (t > 0) result = (float) c2 / t * 100;
        }
        total.close();
        confirmadas.close();
        return result;
    }

    private RegistroToma cursorToRegistro(Cursor c) {
        RegistroToma r = new RegistroToma();
        r.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_ID)));
        r.setIdHorario(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_HORARIO)));
        r.setIdUsuario(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_USUARIO)));
        r.setFechaHoraProgramada(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_PROGRAMADA)));
        r.setFechaHoraConfirmacion(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_CONFIRMACION)));
        r.setEstado(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_REG_ESTADO)));
        return r;
    }
}
