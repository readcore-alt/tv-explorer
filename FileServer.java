package com.example.tvexplorer;

import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public class FileServer extends NanoHTTPD {

    public FileServer(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();

        // Si la PC abre la web (GET), le mandamos la página con el botón de subir
        if (Method.GET.equals(method)) {
            String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                    + "<title>TV File Transfer</title>"
                    + "<style>"
                    + "body{background:#121212;color:#eee;font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}"
                    + ".card{background:#1e1e1e;padding:30px;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,0.5);text-align:center;max-width:400px;width:90%;}"
                    + "h2{margin-top:0;color:#4CAF50;}"
                    + "input[type=file]{margin:20px 0;width:100%;color:#aaa;}"
                    + "button{background:#4CAF50;color:#fff;border:none;padding:12px 24px;font-size:16px;border-radius:6px;cursor:pointer;width:100%;}"
                    + "button:hover{background:#45a049;}"
                    + "</style></head><body>"
                    + "<div class='card'>"
                    + "<h2>📺 TV File Transfer</h2>"
                    + "<p>Selecciona un archivo (APK, video, foto) para enviarlo a la tele:</p>"
                    + "<form method='post' enctype='multipart/form-data'>"
                    + "<input type='file' name='file' required><br>"
                    + "<button type='submit'>Enviar a la TV</button>"
                    + "</form></div></body></html>";
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        }

        // Si la PC mandó un archivo (POST)
        if (Method.POST.equals(method)) {
            Map<String, String> files = new HashMap<>();
            try {
                // NanoHTTPD parsea los datos binarios automáticamente
                session.parseBody(files);
                Map<String, List<String>> params = session.getParameters();
                String fileName = null;
                
                if (params != null && params.containsKey("file") && !params.get("file").isEmpty()) {
                    fileName = params.get("file").get(0);
                }
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "archivo_tv_" + System.currentTimeMillis();
                }

                if (files.containsKey("file")) {
                    File tempFile = new File(files.get("file"));
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) downloadDir.mkdirs();

                    File destFile = new File(downloadDir, fileName);

                    // Copiar del archivo temporal al destino final
                    copyFile(tempFile, destFile);

                    return newFixedLengthResponse(Response.Status.OK, "text/html", 
                            "<body style='background:#121212;color:#4CAF50;text-align:center;padding-top:50px;font-family:sans-serif;'>"
                            + "<h2>✅ ¡Archivo recibido con éxito en la TV!</h2>"
                            + "<p style='color:#ccc;'>Guardado en: /Download/" + fileName + "</p>"
                            + "<br><a href='/' style='color:#2196F3;'>Subir otro archivo</a></body>");
                }
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error al subir: " + e.getMessage());
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado");
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024 * 64]; // Buffers de 64KB para transferencias rápidas
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
