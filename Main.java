
package es.ugr.ri;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.Link;

public class Main {

    private static final Tika tika = new Tika();
    private static LanguageDetector detector;

    private static final ObjectMapper json = new ObjectMapper();
    private static BufferedWriter escritor;

    private static int contador = 0;

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Uso: indicar carpeta de documentos");
            return;
        }

        File carpeta = new File(args[0]);

        if (!carpeta.isDirectory()) {
            System.out.println("No existe el directorio");
            return;
        }

        // Iniciar detector de idiomas
        detector = LanguageDetector.getDefaultLanguageDetector();
        detector.loadModels();

        boolean mostrarDetalles =
                args.length > 1 && args[1].equals("detalle");

        System.out.println("\nINVENTARIO DE DOCUMENTOS");
        System.out.println("========================\n");

        System.out.printf(
                "%-35s | %-30s | %-12s | %-6s | %s%n",
                "FICHERO", "MIME", "CODIFICACION",
                "IDIOMA", "CARACTERES"
        );

        System.out.println("-".repeat(105));

        // Crear el archivo JSONL
        try (BufferedWriter salida =
                Files.newBufferedWriter(Path.of("coleccion.jsonl"))) {

            escritor = salida;
            procesarCarpeta(carpeta, mostrarDetalles);
        }

        System.out.println("\nDocumentos procesados: " + contador);
        System.out.println("Archivo generado: coleccion.jsonl");
    }

    private static void procesarCarpeta(
            File carpeta, boolean mostrarDetalles) throws Exception {

        File[] archivos = carpeta.listFiles();

        if (archivos == null) {
            System.out.println("No se puede leer: " + carpeta.getPath());
            return;
        }

        Arrays.sort(archivos, Comparator.comparing(File::getName));

        for (File archivo : archivos) {

            if (archivo.isDirectory()) {
                procesarCarpeta(archivo, mostrarDetalles);
            } else if (archivo.isFile()) {
                procesarArchivo(archivo, mostrarDetalles);
            }
        }
    }

    private static void procesarArchivo(
            File archivo, boolean mostrarDetalles) throws Exception {

        contador++;

        String id = String.format("doc%04d", contador);
        String tipo = "N/D";
        String idioma = "N/D";
        String codificacion = "N/D";
        String titulo = null;
        String autor = null;
        String texto = "";
        String estado = "OK";

        List<String> enlaces = new ArrayList<>();
        Metadata metadata = new Metadata();

        try {
            // Detectar tipo MIME
            tipo = tika.detect(archivo);

            // Preparar extraccion de texto y enlaces
            BodyContentHandler textoHandler =
                    new BodyContentHandler(-1);

            LinkContentHandler enlacesHandler =
                    new LinkContentHandler();

            TeeContentHandler teeHandler =
                    new TeeContentHandler(
                            textoHandler, enlacesHandler
                    );

            AutoDetectParser parser = new AutoDetectParser();
            ParseContext contexto = new ParseContext();

            try (InputStream entrada =
                    new FileInputStream(archivo)) {

                parser.parse(entrada, teeHandler, metadata, contexto);
            }

            texto = textoHandler.toString();

            // Obtener los enlaces
            for (Link enlace : enlacesHandler.getLinks()) {
                if (enlace.getUri() != null) {
                    enlaces.add(enlace.getUri());
                }
            }

            // Detectar idioma
            if (!texto.isBlank()) {
                LanguageResult resultado = detector.detect(texto);

                if (resultado != null && !resultado.isUnknown()) {
                    idioma = resultado.getLanguage();
                }
            }

            // Obtener codificacion
            String encoding = metadata.get("Content-Encoding");

            if (encoding != null && !encoding.isBlank()) {
                codificacion = encoding;
            }

            // Recuperar titulo y autor cuando esten disponibles
            titulo = metadata.get("dc:title");

            if (titulo == null) {
                titulo = metadata.get("title");
            }

            if (titulo == null) {
                titulo = metadata.get("og:title");
            }

            autor = metadata.get("dc:creator");

            if (autor == null) {
                autor = metadata.get("Author");
            }

            // Mostrar fila del inventario
            System.out.printf(
                    "%-35.35s | %-30.30s | %-12.12s | %-6s | %d%n",
                    archivo.getName(),
                    tipo,
                    codificacion,
                    idioma,
                    texto.length()
            );

            // Mostrar informacion detallada si se solicita
            if (mostrarDetalles) {

                System.out.println("\nDOCUMENTO: " + archivo.getName());

                System.out.println("METADATOS:");

                int mostrados = 0;

                for (String nombre : metadata.names()) {

                    if (mostrados >= 10) break;

                    String valor = metadata.get(nombre);

                    if (valor.length() > 150) {
                        valor = valor.substring(0, 150) + "...";
                    }

                    System.out.println("  " + nombre + ": " + valor);
                    mostrados++;
                }

                System.out.println("ENLACES: " + enlaces.size());

                for (int i = 0; i < Math.min(10, enlaces.size()); i++) {
                    System.out.println("  " + enlaces.get(i));
                }

                System.out.println("TEXTO EXTRAIDO:");

                System.out.println(
                        texto.substring(0, Math.min(300, texto.length()))
                );

                System.out.println("\n--------------------------------\n");
            }

        } catch (Exception e) {
            estado = "ERROR: " + e.getMessage();
            System.err.println(
                    "Error procesando " + archivo.getName() + ": " + e.getMessage()
            );
        }

        // Crear un registro JSON con los campos obligatorios
        Map<String, Object> documento = new LinkedHashMap<>();

        documento.put("id", id);
        documento.put("path", archivo.getPath().replace("\\", "/"));
        documento.put("mime_type", tipo);
        documento.put("language", idioma);
        documento.put("title", titulo);
        documento.put("author", autor);
        documento.put("text", texto);
        documento.put("num_characters", texto.length());
        documento.put("links", enlaces);
        documento.put("status", estado);

        // Escribir un objeto JSON por linea
        escritor.write(json.writeValueAsString(documento));
        escritor.newLine();
    }
}
