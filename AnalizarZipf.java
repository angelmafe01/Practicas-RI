
package es.ugr.ri;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AnalizarZipf {

    public static void main(String[] args) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        // Leer los documentos del JSONL
        List<String> lineas = Files.readAllLines(
                Path.of("coleccion.jsonl"),
                StandardCharsets.UTF_8
        );

        // Agrupar el texto por idioma
        Map<String, StringBuilder> textos = new HashMap<>();

        textos.put("es", new StringBuilder());
        textos.put("en", new StringBuilder());
        textos.put("fr", new StringBuilder());

        for (String linea : lineas) {

            JsonNode documento = mapper.readTree(linea);

            String idioma = documento.path("language").asText();
            String texto = documento.path("text").asText();

            if (textos.containsKey(idioma) && !texto.isBlank()) {

                textos.get(idioma).append(texto).append(" ");
            }
        }

        // Generar un CSV para cada idioma
        for (String idioma : textos.keySet()) {

            String texto = textos.get(idioma).toString();

            if (texto.isBlank()) {
                continue;
            }

            generarCSV(texto, idioma);
        }
    }

    private static void generarCSV(
            String texto, String idioma) throws Exception {

        // Normalizacion basica
        texto = texto.toLowerCase();

        // Separar palabras por caracteres no alfabeticos
        String[] palabras = texto.split("[^\\p{L}]+");

        // Contar frecuencias
        Map<String, Integer> frecuencias = new HashMap<>();

        for (String palabra : palabras) {

            if (!palabra.isBlank()) {

                frecuencias.put(
                        palabra,
                        frecuencias.getOrDefault(palabra, 0) + 1
                );
            }
        }

        // Ordenar por frecuencia descendente
        List<Map.Entry<String, Integer>> ordenadas =
                new ArrayList<>(frecuencias.entrySet());

        ordenadas.sort(
                Map.Entry.<String, Integer>comparingByValue(
                        Comparator.reverseOrder()
                ).thenComparing(Map.Entry.comparingByKey())
        );

        // Generar CSV con rango, termino y frecuencia
        List<String> salida = new ArrayList<>();

        salida.add("Rango;Termino;Frecuencia");

        int rango = 1;

        for (Map.Entry<String, Integer> entrada : ordenadas) {

            salida.add(
                    rango + ";" +
                    entrada.getKey() + ";" +
                    entrada.getValue()
            );

            rango++;
        }

        String nombre = "frecuencias_" + idioma + ".csv";

        Files.write(
                Path.of(nombre),
                salida,
                StandardCharsets.UTF_8
        );

        System.out.println(
                "Idioma: " + idioma +
                " | Terminos diferentes: " + frecuencias.size() +
                " | CSV generado: " + nombre
        );

        System.out.println("10 palabras mas frecuentes:");

        for (int i = 0; i < Math.min(10, ordenadas.size()); i++) {

            System.out.println(
                    (i + 1) + ". " +
                    ordenadas.get(i).getKey() + " -> " +
                    ordenadas.get(i).getValue()
            );
        }

        System.out.println();
    }
}
