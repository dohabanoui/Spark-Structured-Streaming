package ma.dohabanoui;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.OutputMode;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.*;

public class HospitalIncidentsStreaming {
    private static final String APP_NAME = "HospitalIncidentsStreaming";
    private static final String SPARK_MASTER = "spark://spark-master:7077";
    private static final String FILES_LOCATION = "hdfs://namenode:8020/incidents";
    private static final String LOG_LEVEL = "WARN";


    // Crée une session Spark pour gérer les tâches de streaming
    static SparkSession getSparkSession() {
        return SparkSession.builder()
                .appName(APP_NAME)
                .master(SPARK_MASTER)
                .getOrCreate();
    }

    // Définit le schéma des données des incidents
    static StructType getIncidentSchema() {
        return new StructType(new StructField[]{
                new StructField("Id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("titre", DataTypes.StringType, false, Metadata.empty()),
                new StructField("description", DataTypes.StringType, false, Metadata.empty()),
                new StructField("service", DataTypes.StringType, false, Metadata.empty()),
                new StructField("date", DataTypes.DateType, false, Metadata.empty())
        });
    }

    public static void main(String[] args) {

        // Initialise la session Spark pour le traitement des données en streaming
        try (SparkSession session = getSparkSession()) {

            // Configure le niveau de log pour afficher uniquement les avertissements
            session.sparkContext().setLogLevel(LOG_LEVEL);

            // Charge les fichiers CSV d'incidents depuis le répertoire HDFS en streaming
            Dataset<Row> incidents = session.readStream()
                    .schema(getIncidentSchema()) // Spécifie le schéma des incidents
                    .option("header", "true") // Indique que la première ligne contient les entêtes
                    .csv(FILES_LOCATION); // Charge les fichiers CSV à partir de l'emplacement spécifié

            // Première requête : Compte le nombre d'incidents par service
            StreamingQuery countIncidentByService = incidents.groupBy(col("service"))
                    .agg(count("*").alias("Nombre_incidents")) // Effectue une agrégation pour compter les incidents
                    .writeStream()
                    .outputMode(OutputMode.Complete()) // Utilise le mode de sortie complet
                    .format("console") // Affiche les résultats dans la console
                    .start(); // Lance la requête de streaming

            // Deuxième requête : Identifie les deux années ayant le plus grand nombre d'incidents
            StreamingQuery mostIncidentByYear = incidents
                    .groupBy(year(col("date")).alias("Annee_incidents")) // Regroupe les incidents par année
                    .agg(count("*").alias("Nombre_incidents")) // Compte le nombre d'incidents par année
                    .orderBy(col("Nombre_incidents").desc()) // Trie les résultats par nombre d'incidents de manière décroissante
                    .limit(2) // Limite les résultats aux deux années les plus incidentées
                    .writeStream()
                    .outputMode(OutputMode.Complete()) // Mode de sortie complet
                    .format("console") // Affiche les résultats dans la console
                    .start(); // Lance la requête de streaming

            // Attend que les deux requêtes de streaming se terminent
            countIncidentByService.awaitTermination();
            mostIncidentByYear.awaitTermination();

        } catch (TimeoutException | StreamingQueryException e) {
            // Gère les exceptions liées aux erreurs de streaming ou aux délais d'attente
            throw new RuntimeException(e);
        }
    }
}
