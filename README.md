# Description du projet

L’application lit les fichiers CSV contenant des incidents hospitaliers depuis **HDFS** (Hadoop Distributed File System) et effectue deux opérations principales :

1. **Affichage continu du nombre d’incidents par service** :  
   Cette opération regroupe les incidents par service et affiche le nombre d’incidents dans chaque service de manière continue.

2. **Affichage continu des deux années avec le plus grand nombre d’incidents** :  
   Cette opération regroupe les incidents par année et affiche les deux années avec le plus grand nombre d’incidents.


## Technologies utilisées

- **Apache Spark**
- **HDFS** (Hadoop Distributed File System)
- **Docker** (pour déployer les services Hadoop et Spark)


## Prérequis

1. **Docker** et **Docker Compose** pour déployer les services HDFS et Spark.
2. **Apache Maven** pour la gestion du projet et des dépendances.
3. **Java 8** (ou version supérieure) installé.

## 4. La classe `HospitalIncidentsStreaming`

### 4.1 Déclaration des constantes

```java
private static final String APP_NAME = "HospitalIncidentsStreaming";
private static final String SPARK_MASTER = "spark://spark-master:7077";
private static final String FILES_LOCATION = "hdfs://namenode:8020/incidents";
private static final String LOG_LEVEL = "WARN";
```

### 4.2 Fonction `getSparkSession()`

La fonction configure et initialise une session Spark. Cette session est utilisée pour toutes les opérations de traitement et de streaming.

```java
static SparkSession getSparkSession() {
    return SparkSession.builder()
        .appName(APP_NAME)
        .master(SPARK_MASTER)
        .getOrCreate();
}
```

### 4.3 Fonction `getIncidentSchema()`

Cette fonction définit le schéma des données des incidents à l’aide de la classe `StructType`. Le schéma spécifie les colonnes que l’on va traiter dans le flux de données, ainsi que leur type respectif.  

Les colonnes sont :  
- **Id**  
- **Titre**  
- **Description**  
- **Service**  
- **Date**  

Cette structure est cruciale pour que Spark sache comment interpréter les données lues dans les fichiers CSV.

```java
static StructType getIncidentSchema() {
    return new StructType(new StructField[]{
        new StructField("Id", DataTypes.IntegerType, false, Metadata.empty()),
        new StructField("titre", DataTypes.StringType, false, Metadata.empty()),
        new StructField("description", DataTypes.StringType, false, Metadata.empty()),
        new StructField("service", DataTypes.StringType, false, Metadata.empty()),
        new StructField("date", DataTypes.DateType, false, Metadata.empty())
    });
}
```
### 4.4 Bloc principal (`main`)

Initialisation de la session Spark et configuration des logs :

```java
try (SparkSession session = getSparkSession()) {
    session.sparkContext().setLogLevel(LOG_LEVEL);
}
```

### 4.5 Lecture des fichiers CSV en streaming

Cette section utilise la méthode `readStream()` de Spark pour lire les fichiers CSV contenant des incidents de manière continue.  
Le schéma des incidents est défini, et Spark sait que la première ligne contient les en-têtes. Cela permet de charger les données en temps réel pour un traitement ultérieur.

```java
Dataset<Row> incidents = session.readStream()
    .schema(getIncidentSchema()) // Spécifie le schéma des incidents
    .option("header", "true")    // Indique que la première ligne contient les en-têtes
    .csv(FILES_LOCATION);        // Charge les fichiers CSV depuis l'emplacement spécifié
```

### 4.6 Première requête de streaming

Cette requête effectue une agrégation pour compter le nombre d’incidents par service.  
Les résultats sont affichés dans la console à chaque mise à jour du flux.  

Le mode `Complete()` garantit que l’ensemble des données agrégées est réécrit à chaque nouvelle arrivée de données.

```java
StreamingQuery countIncidentByService = incidents.groupBy(col("service"))
    .agg(count("*").alias("Nombre_incidents")) // Effectue une agrégation pour compter les incidents
    .writeStream()
    .outputMode(OutputMode.Complete())         // Utilise le mode de sortie complet
    .format("console");                        // Affiche les résultats dans la console
```

### 4.7 Deuxième requête de streaming

Cette requête regroupe les incidents par année et affiche les deux années ayant le plus grand nombre d'incidents.  

Le mode `Complete()` garantit que l’ensemble des données agrégées est réécrit à chaque nouvelle arrivée de données.

```java
StreamingQuery mostIncidentByYear = incidents
    .groupBy(year(col("date")).alias("Annee_incidents")) // Regroupe les incidents par année
    .agg(count("*").alias("Nombre_incidents"))           // Compte le nombre d'incidents par année
    .orderBy(col("Nombre_incidents").desc())             // Trie les résultats par nombre d'incidents décroissant
    .limit(2)                                            // Limite les résultats aux deux années les plus incidentées
    .writeStream()
    .outputMode(OutputMode.Complete())                   // Mode de sortie complet
    .format("console")                                   // Affiche les résultats dans la console
    .start();
```

## Test

Pour générer le fichier JAR, vous pouvez utiliser Apache Maven, qui est configuré pour compiler le projet et créer le fichier exécutable.
![](captures/5.png)

### Démarrer avec Docker Compose et Ajouter une Image Docker

Pour démarrer les services avec Docker Compose et spécifier que vous souhaitez avoir deux nœuds `datanode`, vous pouvez utiliser la commande suivante :

```bash
docker-compose up --scale datanode=2 -d
```
![](captures/1.png)

### Afficher les conteneurs Docker en cours d'exécution

Pour voir les conteneurs Docker en cours d'exécution, on utilise la commande suivante :

```bash
docker ps
```
![](captures/2.png)

### Exécuter le conteneur Namenode avec Docker

Pour accéder à un conteneur `namenode` en cours d'exécution et obtenir un terminal interactif, vous pouvez utiliser la commande suivante :

```bash
docker exec -it f2f4bc98dca8 bash
```
![](captures/3.png)
![](captures/4.png)

### Déplacer le fichier JAR dans le dossier Volume en Data

Après avoir généré le fichier JAR, vous pouvez le déplacer dans un dossier volume de données Docker pour qu'il soit accessible depuis d'autres services ou conteneurs. 
![](captures/6.png)

### Soumettre le fichier JAR à Spark

Une fois le fichier JAR déplacé dans le volume de données de votre conteneur, vous pouvez le soumettre à Spark pour exécution avec la commande `spark-submit`. Voici la commande à utiliser :

```bash
spark-submit --master spark://spark-master:7077 --class ma.dohabanoui.HospitalIncidentsStreaming spark_structed-1.0-SNAPSHOT.jar
```
### Chargement des Fichiers CSV dans HDFS

Afin de traiter les fichiers CSV contenant les données des incidents hospitaliers, nous devons les placer dans le système de fichiers distribué HDFS. Voici comment charger les fichiers `incident1.csv`, `incident2.csv`, et `incident3.csv` dans le répertoire `/incidents` de HDFS :

**Charger le fichier `incident1.csv` dans HDFS :**
   ```bash
   hdfs dfs -put incident1.csv /incidents
```
![](captures/7.png)
![](captures/8.png)
![](captures/9.png)
![](captures/10.png)
![](captures/11.png)


