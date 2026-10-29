package com.example.tvexplorer;

import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public class FileServer extends NanoHTTPD {

    public interface TransferListener {
        void onFileReceived(String fileName, int index, int total);
    }

    public static TransferListener listener = null;

    public FileServer(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();

        // 1. PÁGINA WEB MODERNA PARA LA PC (Drag & Drop + Medidor de MB/s real)
        if (Method.GET.equals(method)) {
            String html = "<!DOCTYPE html><html lang='es'><head><meta charset='utf-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                    + "<title>TV Explorer Transfer</title>"
                    + "<style>"
                    + "* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }"
                    + "body { background: #0f0f12; color: #f0f0f0; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 20px; }"
                    + ".card { background: #181920; border: 1px solid #282a36; border-radius: 16px; padding: 32px; max-width: 480px; width: 100%; box-shadow: 0 10px 40px rgba(0,0,0,0.6); text-align: center; }"
                    + "h2 { font-size: 24px; margin-bottom: 8px; color: #2196F3; }"
                    + "p { color: #888; font-size: 14px; margin-bottom: 24px; }"
                    + ".dropzone { border: 2px dashed #3d4255; border-radius: 12px; padding: 36px 20px; cursor: pointer; transition: 0.2s; background: #13141a; }"
                    + ".dropzone:hover, .dropzone.dragover { border-color: #2196F3; background: #171b26; }"
                    + ".dropzone svg { width: 48px; height: 48px; fill: #2196F3; margin-bottom: 12px; }"
                    + ".dropzone span { display: block; font-size: 15px; color: #ccc; font-weight: 500; }"
                    + ".dropzone small { display: block; color: #666; font-size: 12px; margin-top: 6px; }"
                    + "#fileInput { display: none; }"
                    + "#progressContainer { margin-top: 24px; display: none; text-align: left; }"
                    + ".progress-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px; color: #bbb; }"
                    + ".progress-bar { width: 100%; height: 8px; background: #282a36; border-radius: 4px; overflow: hidden; }"
                    + ".progress-fill { width: 0%; height: 100%; background: #4CAF50; transition: width 0.15s ease-out; }"
                    + "#statusText { font-size: 12px; color: #888; margin-top: 6px; text-align: right; }"
                    + "</style></head><body>"
                    + "<div class='card'>"
                    + "<h2>📺 TV Explorer</h2>"
                    + "<p>Envía películas, música, fotos y APKs a la tele</p>"
                    + "<div class='dropzone' id='dropzone'>"
                    + "<svg viewBox='0 0 24 24'><path d='M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z'/></svg>"
                    + "<span>Arrastra tus archivos aquí</span>"
                    + "<small>o haz clic para explorar en tu PC</small>"
                    + "</div>"
                    + "<input type='file' id='fileInput' multiple>"
                    + "<div id='progressContainer'>"
                    + "<div class='progress-header'><span id='fileName'>Subiendo...</span><span id='percentText'>0%</span></div>"
                    + "<div class='progress-bar'><div class='progress-fill' id='progressFill'></div></div>"
                    + "<div id='statusText'>Preparando envío...</div>"
                    + "</div>"
                    + "</div>"
                    + "<script>"
                    + "const dropzone = document.getElementById('dropzone');"
                    + "const fileInput = document.getElementById('fileInput');"
                    + "const progressContainer = document.getElementById('progressContainer');"
                    + "const progressFill = document.getElementById('progressFill');"
                    + "const percentText = document.getElementById('percentText');"
                    + "const fileNameText = document.getElementById('fileName');"
                    + "const statusText = document.getElementById('statusText');"
                    + "dropzone.onclick = () => fileInput.click();"
                    + "dropzone.ondragover = (e) => { e.preventDefault(); dropzone.classList.add('dragover'); };"
                    + "dropzone.ondragleave = () => dropzone.classList.remove('dragover');"
                    + "dropzone.ondrop = (e) => { e.preventDefault(); dropzone.classList.remove('dragover'); if (e.dataTransfer.files.length) uploadQueue(e.dataTransfer.files); };"
                    + "fileInput.onchange = () => { if (fileInput.files.length) uploadQueue(fileInput.files); };"
                    + "async function uploadQueue(files) {"
                    + "  progressContainer.style.display = 'block';"
                    + "  for (let i = 0; i < files.length; i++) {"
                    + "    await uploadFile(files[i], i + 1, files.length);"
                    + "  }"
                    + "  fileNameText.innerText = '✅ ¡Todos los archivos recibidos!';"
                    + "  statusText.innerText = 'Guardados en /Download';"
                    + "  progressFill.style.width = '100%';"
                    + "}"
                    + "function uploadFile(file, index, total) {"
                    + "  return new Promise((resolve, reject) => {"
                    + "    const xhr = new XMLHttpRequest();"
                    + "    const formData = new FormData();"
                    + "    formData.append('file', file, file.name);"
                    + "    fileNameText.innerText = `(${index}/${total}) ${file.name}`;"
                    + "    let startTime = Date.now();"
                    + "    xhr.upload.onprogress = (e) => {"
                    + "      if (e.lengthComputable) {"
                    + "        const percent = Math.round((e.loaded / e.total) * 100);"
                    + "        progressFill.style.width = percent + '%';"
                    + "        percentText.innerText = percent + '%';"
                    + "        const elapsed = (Date.now() - startTime) / 1000;"
                    + "        const speedMB = elapsed > 0 ? (e.loaded / (1024 * 1024 * elapsed)).toFixed(1) : 0;"
                    + "        statusText.innerText = `${(e.loaded/(1024*1024)).toFixed(1)} MB / ${(e.total/(1024*1024)).toFixed(1)} MB (${speedMB} MB/s)`;"
                    + "      }"
                    + "    };"
                    + "    xhr.onload = () => resolve();"
                    + "    xhr.onerror = () => reject();"
                    + "    xhr.open('POST', `/?index=${index}&total=${total}`, true);"
                    + "    xhr.send(formData);"
                    + "  });"
                    + "}"
                    + "</script></body></html>";
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        }

        // 2. RECEPCIÓN NATIVA ULTRA RÁPIDA (parseBody nativo de NanoHTTPD)
        if (Method.POST.equals(method)) {
            Map<String, String> files = new HashMap<>();
            try {
                session.parseBody(files);

                // Obtener nombre original del archivo
                String fileName = session.getParms().get("file");
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "archivo_tv_" + System.currentTimeMillis();
                }

                Map<String, String> parms = session.getParms();
                int index = parms.containsKey("index") ? Integer.parseInt(parms.get("index")) : 1;
                int total = parms.containsKey("total") ? Integer.parseInt(parms.get("total")) : 1;

                if (files.containsKey("file")) {
                    File tempFile = new File(files.get("file"));
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) downloadDir.mkdirs();

                    File destFile = new File(downloadDir, fileName);

                    // Mover de forma instantánea si es posible, o copiar con buffer
                    if (!tempFile.renameTo(destFile)) {
                        copyFile(tempFile, destFile);
                        tempFile.delete();
                    }

                    if (listener != null) {
                        listener.onFileReceived(fileName, index, total);
                    }

                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado");
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
