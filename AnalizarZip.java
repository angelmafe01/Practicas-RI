package es.ugr.ri;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class AnalizarZip {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("Uso: indicar la ruta del ZIP");
            return;
        }

        File archivo = new File(args[0]);

        if (!archivo.isFile()) {
            System.out.println("No existe el archivo indicado");
            return;
        }

        // Parser que detecta automaticamente los formatos
        AutoDetectParser parser = new AutoDetectParser();

        // Permite procesar archivos dentro de otros archivos
        RecursiveParserWrapper wrapper =
                new RecursiveParserWrapper(parser);

        // Guarda los resultados de cada documento encontrado
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                        -1
                    )
                );

        Metadata metadata = new Metadata();
        ParseContext contexto = new ParseContext();

        // Abrir y analizar el ZIP
        try (InputStream entrada = new FileInputStream(archivo)) {
            wrapper.parse(entrada, handler, metadata, contexto);
        }

        // Recuperar los metadatos del ZIP y sus archivos internos
        List<Metadata> documentos = handler.getMetadataList();

        System.out.println("\nANALISIS DEL DOCUMENTO COMPUESTO");
        System.out.println("================================");
        System.out.println("Archivo principal: " + archivo.getName());
        System.out.println("Registros encontrados: " + documentos.size());

        int numero = 1;

        for (Metadata datos : documentos) {

            String nombre = datos.get("resourceName");
            String tipo = datos.get("Content-Type");

            if (nombre == null || nombre.isBlank()) {
                nombre = "Documento principal";
            }

            System.out.println("\nRecurso " + numero);
            System.out.println("Nombre: " + nombre);
            System.out.println("Tipo MIME: " + tipo);

            numero++;
        }
    }
}
