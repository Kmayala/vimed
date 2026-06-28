package com.tesis.vimed.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tesis.vimed.models.Usuario;

public class UsuarioDAO {
    private final DatabaseHelper dbHelper;

    public UsuarioDAO(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertar(Usuario u) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_USR_NOMBRE, u.getNombre());
        cv.put(DatabaseHelper.COL_USR_CORREO, u.getCorreo());
        cv.put(DatabaseHelper.COL_USR_CONTRASENA, u.getContrasena());
        cv.put(DatabaseHelper.COL_USR_ROL, u.getRol() != null ? u.getRol() : "");
        return db.insert(DatabaseHelper.TABLE_USUARIOS, null, cv);
    }

    public Usuario buscarPorCorreo(String correo) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DatabaseHelper.TABLE_USUARIOS, null,
            DatabaseHelper.COL_USR_CORREO + "=?", new String[]{correo},
            null, null, null);
        if (c.moveToFirst()) {
            Usuario u = cursorToUsuario(c);
            c.close();
            return u;
        }
        c.close();
        return null;
    }

    public Usuario buscarPorId(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DatabaseHelper.TABLE_USUARIOS, null,
            DatabaseHelper.COL_USR_ID + "=?", new String[]{String.valueOf(id)},
            null, null, null);
        if (c.moveToFirst()) {
            Usuario u = cursorToUsuario(c);
            c.close();
            return u;
        }
        c.close();
        return null;
    }

    public boolean validarLogin(String correo, String contrasena) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DatabaseHelper.TABLE_USUARIOS, null,
            DatabaseHelper.COL_USR_CORREO + "=? AND " + DatabaseHelper.COL_USR_CONTRASENA + "=?",
            new String[]{correo, contrasena}, null, null, null);
        boolean valido = c.moveToFirst();
        c.close();
        return valido;
    }

    public int actualizarRol(int idUsuario, String rol) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_USR_ROL, rol);
        return db.update(DatabaseHelper.TABLE_USUARIOS, cv,
            DatabaseHelper.COL_USR_ID + "=?", new String[]{String.valueOf(idUsuario)});
    }

    private Usuario cursorToUsuario(Cursor c) {
        Usuario u = new Usuario();
        u.setId(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_USR_ID)));
        u.setNombre(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_USR_NOMBRE)));
        u.setCorreo(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_USR_CORREO)));
        u.setRol(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_USR_ROL)));
        return u;
    }
}
