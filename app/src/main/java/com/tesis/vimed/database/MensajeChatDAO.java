package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.MensajeChat;

import java.util.ArrayList;
import java.util.List;

public class MensajeChatDAO {
    private final DatabaseHelper dbHelper;

    public MensajeChatDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(MensajeChat m) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_CHAT_USUARIO, m.getIdUsuario());
        cv.put(DatabaseHelper.COL_CHAT_ROL, m.getRol());
        cv.put(DatabaseHelper.COL_CHAT_CONTENIDO, m.getContenido());
        return db.insert(DatabaseHelper.TABLE_CHAT, null, cv);
    }

    public List<MensajeChat> listarPorUsuario(int idUsuario) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<MensajeChat> lista = new ArrayList<>();
        Cursor c = db.query(DatabaseHelper.TABLE_CHAT, null,
            DatabaseHelper.COL_CHAT_USUARIO + "=?",
            new String[]{String.valueOf(idUsuario)},
            null, null, DatabaseHelper.COL_CHAT_FECHA + " ASC");
        while (c.moveToNext()) lista.add(cursorToMensaje(c));
        c.close();
        return lista;
    }

    public void borrarHistorial(int idUsuario) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_CHAT,
            DatabaseHelper.COL_CHAT_USUARIO + "=?",
            new String[]{String.valueOf(idUsuario)});
    }

    private MensajeChat cursorToMensaje(Cursor c) {
        MensajeChat m = new MensajeChat();
        m.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_ID)));
        m.setIdUsuario(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_USUARIO)));
        m.setRol(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_ROL)));
        m.setContenido(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_CONTENIDO)));
        m.setFechaHora(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CHAT_FECHA)));
        return m;
    }
}
