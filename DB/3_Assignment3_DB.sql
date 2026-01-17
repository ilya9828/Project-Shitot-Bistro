-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: localhost    Database: bistrodb
-- ------------------------------------------------------
-- Server version	8.0.44

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `managerids`
--

DROP TABLE IF EXISTS `managerids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `managerids` (
  `managerID` varchar(10) NOT NULL,
  PRIMARY KEY (`managerID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `managerids`
--

LOCK TABLES `managerids` WRITE;
/*!40000 ALTER TABLE `managerids` DISABLE KEYS */;
INSERT INTO `managerids` VALUES ('m0537');
/*!40000 ALTER TABLE `managerids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `opening_hours`
--

DROP TABLE IF EXISTS `opening_hours`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `opening_hours` (
  `id` int NOT NULL AUTO_INCREMENT,
  `day_of_week` varchar(20) NOT NULL,
  `opening_time` time NOT NULL,
  `closing_time` time NOT NULL,
  `specific_date` date DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `is_permanent` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_day_date` (`day_of_week`,`specific_date`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `opening_hours`
--

LOCK TABLES `opening_hours` WRITE;
/*!40000 ALTER TABLE `opening_hours` DISABLE KEYS */;
INSERT INTO `opening_hours` VALUES (1,'Sunday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(2,'Monday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(3,'Tuesday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(4,'Wednesday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(5,'Thursday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(6,'Friday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01'),(7,'Saturday','08:00:00','18:00:00',NULL,1,1,'2026-01-17 17:42:01');
/*!40000 ALTER TABLE `opening_hours` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_number` varchar(50) NOT NULL,
  `confirmation_code` varchar(20) NOT NULL,
  `subscriber_id` int DEFAULT NULL,
  `number_of_guests` int NOT NULL,
  `order_time_date` datetime NOT NULL,
  `time_date_of_placing_order` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(35) NOT NULL,
  `is_subscriber` tinyint(1) DEFAULT '0',
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `reminder_sent` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Flag to track if reminder notification has been sent (0=not sent, 1=sent)',
  PRIMARY KEY (`order_number`),
  UNIQUE KEY `confirmation_code` (`confirmation_code`),
  KEY `idx_confirmation_code` (`confirmation_code`),
  KEY `idx_order_status` (`status`),
  KEY `idx_order_datetime` (`order_time_date`),
  KEY `idx_subscriber_id` (`subscriber_id`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`subscriber_id`) REFERENCES `subscriber` (`subscriberID`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES ('DEC-CBR-1','DEC-CBR-1',NULL,2,'2025-12-09 18:00:00','2025-12-02 08:00:00','Cancelled by resturant',0,'cbr1@test.com','0500000021','CBR A',0),('DEC-CBR-2','DEC-CBR-2',NULL,5,'2025-12-10 19:00:00','2025-12-02 09:00:00','Cancelled by resturant',0,'cbr2@test.com','0500000022','CBR B',0),('DEC-CBR-3','DEC-CBR-3',NULL,3,'2025-12-11 18:00:00','2025-12-02 10:00:00','Cancelled by resturant',0,'cbr3@test.com','0500000023','CBR C',0),('DEC-CBU-1','DEC-CBU-1',NULL,4,'2025-12-13 18:00:00','2025-12-04 08:00:00','Cancelled by user',0,'cbu1@test.com','0500000041','CBU A',0),('DEC-CBU-2','DEC-CBU-2',NULL,2,'2025-12-14 19:00:00','2025-12-04 09:00:00','Cancelled by user',0,'cbu2@test.com','0500000042','CBU B',0),('DEC-CBU-3','DEC-CBU-3',NULL,3,'2025-12-15 18:00:00','2025-12-04 10:00:00','Cancelled by user',0,'cbu3@test.com','0500000043','CBU C',0),('DEC-CBU-4','DEC-CBU-4',NULL,2,'2025-12-16 19:00:00','2025-12-04 11:00:00','Cancelled by user',0,'cbu4@test.com','0500000044','CBU D',0),('DEC-L14-1','DEC-L14-1',NULL,3,'2025-12-07 18:00:00','2025-12-01 10:00:00','paid',0,'l141@test.com','0500000011','Late 1-14 A',0),('DEC-L14-2','DEC-L14-2',NULL,2,'2025-12-08 18:30:00','2025-12-01 10:30:00','paid',0,'l142@test.com','0500000012','Late 1-14 B',0),('DEC-ON1','DEC-ON1',NULL,2,'2025-12-05 18:00:00','2025-12-01 08:00:00','paid',0,'on1@test.com','0500000001','On Time 1',0),('DEC-WL-SEATED1','DEC-WL-SEATED1',NULL,2,'2025-12-05 19:00:00','2025-12-05 16:30:00','paid',0,'wlseated1@test.com','0500100001','Waiting List Seated 1',0),('DEC-WL-SEATED2','DEC-WL-SEATED2',NULL,3,'2025-12-08 19:30:00','2025-12-08 17:00:00','paid',0,'wlseated2@test.com','0500100002','Waiting List Seated 2',0),('DEC-WL-SEATED3','DEC-WL-SEATED3',NULL,4,'2025-12-10 20:00:00','2025-12-10 17:30:00','paid',0,'wlseated3@test.com','0500100003','Waiting List Seated 3',0),('DEC-WL-SEATED4','DEC-WL-SEATED4',NULL,2,'2025-12-12 20:30:00','2025-12-12 18:00:00','paid',0,'wlseated4@test.com','0500100004','Waiting List Seated 4',0),('ORD-1768682560862','QQD76X',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:41','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682562408','WD4AQ4',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:42','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682563667','N597GZ',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:44','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682564772','GV8GWK',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:45','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682565972','LMAEWH',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:46','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682567223','DDKPAK',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:47','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682568379','U6TVSG',527333262,2,'2026-01-21 16:00:00','2026-01-17 20:42:48','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682576358','HRV2UH',527333262,2,'2026-01-21 15:00:00','2026-01-17 20:42:56','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682582261','32S77P',527333262,6,'2026-01-21 16:00:00','2026-01-17 20:43:02','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0),('ORD-1768682584084','EYVAHC',527333262,6,'2026-01-21 16:00:00','2026-01-17 20:43:04','PENDING',1,'adir@gmail.com','0527333262','adir avitan',0);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `report_history`
--

DROP TABLE IF EXISTS `report_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_history` (
  `id` int NOT NULL AUTO_INCREMENT,
  `report_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `months` json NOT NULL,
  `period_label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `months_key` varchar(255) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS (json_unquote(json_extract(`months`,_utf8mb4'$'))) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_report_type_months` (`report_type`,`months_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2291 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report_history`
--

LOCK TABLES `report_history` WRITE;
/*!40000 ALTER TABLE `report_history` DISABLE KEYS */;
INSERT INTO `report_history` (`id`, `report_type`, `months`, `period_label`, `payload`, `created_at`) VALUES (1,'reservation','[\"2025-12\"]','DECEMBER 2025','DECEMBER 2025|68|100.00|0|0.00|0|0|0.00|0|0.00|68|0','2026-01-01 00:00:00'),(3,'delay','[\"2025-11\", \"2025-12\"]','NOVEMBER 2025 + DECEMBER 2025','NOVEMBER 2025 + DECEMBER 2025|20|18.69|16|14.95|12|11.21|6|5.61|23|54.76|19|45.24|107','2026-01-01 00:00:00'),(4,'delay','[\"2025-10\", \"2025-11\", \"2025-12\"]','OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025','OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025|20|18.69|16|14.95|12|11.21|6|5.61|23|54.76|19|45.24|107','2026-01-01 00:00:00'),(5,'delay','[\"2025-12\"]','DECEMBER 2025','DECEMBER 2025|10|14.71|8|11.76|6|8.82|3|4.41|13|61.90|8|38.10|68','2026-01-01 00:00:00'),(8,'reservation','[\"2025-11\", \"2025-12\"]','NOVEMBER 2025 + DECEMBER 2025','NOVEMBER 2025 + DECEMBER 2025|107|95.54|5|4.46|10|5|50.00|5|50.00|112|10','2026-01-01 00:00:00'),(9,'reservation','[\"2025-10\", \"2025-11\", \"2025-12\"]','OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025','OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025|107|95.54|5|4.46|10|5|50.00|5|50.00|112|10','2026-01-01 00:00:00'),(15,'delay','[\"2025-01\", \"2025-02\", \"2025-03\", \"2025-04\", \"2025-05\", \"2025-06\", \"2025-07\", \"2025-08\", \"2025-09\", \"2025-10\", \"2025-11\", \"2025-12\"]','JANUARY 2025 + FEBRUARY 2025 + MARCH 2025 + APRIL 2025 + MAY 2025 + JUNE 2025 + JULY 2025 + AUGUST 2025 + SEPTEMBER 2025 + OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025','JANUARY 2025 + FEBRUARY 2025 + MARCH 2025 + APRIL 2025 + MAY 2025 + JUNE 2025 + JULY 2025 + AUGUST 2025 + SEPTEMBER 2025 + OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025|30|22.06|24|17.65|18|13.24|9|6.62|33|52.38|30|47.62|136','2026-01-01 00:00:00'),(18,'delay','[\"2025-09\", \"2025-10\", \"2025-11\", \"2025-12\"]','SEPTEMBER 2025 + OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025','SEPTEMBER 2025 + OCTOBER 2025 + NOVEMBER 2025 + DECEMBER 2025|20|18.69|16|14.95|12|11.21|6|5.61|23|54.76|19|45.24|107','2026-01-01 00:00:00'),(20,'delay','[\"2025-01\"]','JANUARY 2025','JANUARY 2025|10|41.67|8|33.33|6|25.00|3|12.50|10|47.62|11|52.38|24','2025-02-01 00:00:00'),(21,'reservation','[\"2025-01\"]','JANUARY 2025','JANUARY 2025|24|100.00|0|0.00|0|0|0.00|0|0.00|24|0','2025-02-01 00:00:00'),(22,'delay','[\"2025-02\"]','FEBRUARY 2025','FEBRUARY 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-03-01 00:00:00'),(23,'reservation','[\"2025-02\"]','FEBRUARY 2025','FEBRUARY 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-03-01 00:00:00'),(24,'delay','[\"2025-03\"]','MARCH 2025','MARCH 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-04-01 00:00:00'),(25,'reservation','[\"2025-03\"]','MARCH 2025','MARCH 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-04-01 00:00:00'),(26,'delay','[\"2025-04\"]','APRIL 2025','APRIL 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-05-01 00:00:00'),(27,'reservation','[\"2025-04\"]','APRIL 2025','APRIL 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-05-01 00:00:00'),(28,'delay','[\"2025-05\"]','MAY 2025','MAY 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-06-01 00:00:00'),(29,'reservation','[\"2025-05\"]','MAY 2025','MAY 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-06-01 00:00:00'),(30,'delay','[\"2025-06\"]','JUNE 2025','JUNE 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-07-01 00:00:00'),(31,'reservation','[\"2025-06\"]','JUNE 2025','JUNE 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-07-01 00:00:00'),(32,'delay','[\"2025-07\"]','JULY 2025','JULY 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|5','2025-08-01 00:00:00'),(33,'reservation','[\"2025-07\"]','JULY 2025','JULY 2025|5|41.67|7|58.33|12|7|58.33|5|41.67|12|12','2025-08-01 00:00:00'),(34,'delay','[\"2025-08\"]','AUGUST 2025','AUGUST 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-09-01 00:00:00'),(35,'reservation','[\"2025-08\"]','AUGUST 2025','AUGUST 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-09-01 00:00:00'),(36,'delay','[\"2025-09\"]','SEPTEMBER 2025','SEPTEMBER 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-10-01 00:00:00'),(37,'reservation','[\"2025-09\"]','SEPTEMBER 2025','SEPTEMBER 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-10-01 00:00:00'),(38,'delay','[\"2025-10\"]','OCTOBER 2025','OCTOBER 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-11-01 00:00:00'),(39,'reservation','[\"2025-10\"]','OCTOBER 2025','OCTOBER 2025|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0','2025-11-01 00:00:00'),(40,'delay','[\"2025-11\"]','NOVEMBER 2025','NOVEMBER 2025|10|25.64|8|20.51|6|15.38|3|7.69|10|47.62|11|52.38|39','2025-12-01 00:00:00'),(41,'reservation','[\"2025-11\"]','NOVEMBER 2025','NOVEMBER 2025|39|88.64|5|11.36|10|5|50.00|5|50.00|44|10','2025-12-01 00:00:00'),(217,'delay','[\"2024-01\", \"2024-02\", \"2024-03\", \"2024-04\", \"2024-05\", \"2024-06\", \"2024-07\", \"2024-08\", \"2024-09\", \"2024-10\", \"2024-11\", \"2024-12\", \"2025-04\", \"2025-08\", \"2025-11\", \"2025-12\"]','JANUARY 2024 + FEBRUARY 2024 + MARCH 2024 + APRIL 2024 + MAY 2024 + JUNE 2024 + JULY 2024 + AUGUST 2024 + SEPTEMBER 2024 + OCTOBER 2024 + NOVEMBER 2024 + DECEMBER 2024 + APRIL 2025 + AUGUST 2025 + NOVEMBER 2025 + DECEMBER 2025','JANUARY 2024 + FEBRUARY 2024 + MARCH 2024 + APRIL 2024 + MAY 2024 + JUNE 2024 + JULY 2024 + AUGUST 2024 + SEPTEMBER 2024 + OCTOBER 2024 + NOVEMBER 2024 + DECEMBER 2024 + APRIL 2025 + AUGUST 2025 + NOVEMBER 2025 + DECEMBER 2025|20|18.69|16|14.95|12|11.21|6|5.61|23|54.76|19|45.24|107','2026-01-01 00:00:00'),(220,'delay','[\"2025-03\", \"2025-04\", \"2025-08\"]','MARCH 2025 + APRIL 2025 + AUGUST 2025','MARCH 2025 + APRIL 2025 + AUGUST 2025|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-09-01 00:00:00'),(221,'delay','[\"2025-07\", \"2025-12\"]','JULY 2025 + DECEMBER 2025','JULY 2025 + DECEMBER 2025|10|13.70|8|10.96|6|8.22|3|4.11|13|61.90|8|38.10|73','2026-01-01 00:00:00'),(222,'delay','[\"2024-12\"]','DECEMBER 2024','DECEMBER 2024|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2025-01-01 00:00:00'),(281,'delay','[\"2026-06\", \"2026-08\"]','JUNE 2026 + AUGUST 2026','JUNE 2026 + AUGUST 2026|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2026-09-01 00:00:00'),(282,'delay','[\"2025-06\", \"2025-07\", \"2025-12\"]','JUNE 2025 + JULY 2025 + DECEMBER 2025','JUNE 2025 + JULY 2025 + DECEMBER 2025|10|13.70|8|10.96|6|8.22|3|4.11|13|61.90|8|38.10|73','2026-01-01 00:00:00'),(284,'reservation','[\"2025-06\", \"2025-07\", \"2025-12\"]','JUNE 2025 + JULY 2025 + DECEMBER 2025','JUNE 2025 + JULY 2025 + DECEMBER 2025|73|91.25|7|8.75|12|7|58.33|5|41.67|80|12','2026-01-01 00:00:00'),(1897,'reservation','[\"2024-12\"]','DECEMBER 2024','DECEMBER 2024|10|100.00|0|0.00|0|0|0.00|0|0.00|10|0','2025-01-01 00:00:00'),(2198,'delay','[\"2024-02\"]','FEBRUARY 2024','FEBRUARY 2024|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0','2024-03-01 00:00:00');
/*!40000 ALTER TABLE `report_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `staffids`
--

DROP TABLE IF EXISTS `staffids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staffids` (
  `staffID` varchar(10) NOT NULL,
  PRIMARY KEY (`staffID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `staffids`
--

LOCK TABLES `staffids` WRITE;
/*!40000 ALTER TABLE `staffids` DISABLE KEYS */;
INSERT INTO `staffids` VALUES ('s0527');
/*!40000 ALTER TABLE `staffids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subscriber`
--

DROP TABLE IF EXISTS `subscriber`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscriber` (
  `subscriberID` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`subscriberID`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_subscriber_email` (`email`),
  KEY `idx_subscriber_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=1234567891 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscriber`
--

LOCK TABLES `subscriber` WRITE;
/*!40000 ALTER TABLE `subscriber` DISABLE KEYS */;
INSERT INTO `subscriber` VALUES (521234567,'Bar','bar@gmail.com','0537232077','2025-12-27 00:00:00'),(526287980,'ilya','ilya.simanovski@gmail.com','0526287980','2025-12-21 21:51:37'),(527333262,'adir avitan','adir@gmail.com','0527333262','2025-12-26 00:00:00'),(1234567890,'ido','ido@gmail.com','0526046808','2026-01-08 18:09:51');
/*!40000 ALTER TABLE `subscriber` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tables`
--

DROP TABLE IF EXISTS `tables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tables` (
  `tableID` int NOT NULL AUTO_INCREMENT,
  `capacity` int NOT NULL,
  `tableStatus` varchar(20) DEFAULT 'AVAILABLE',
  `confirmationCode` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`tableID`),
  KEY `idx_table_status` (`tableStatus`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tables`
--

LOCK TABLES `tables` WRITE;
/*!40000 ALTER TABLE `tables` DISABLE KEYS */;
INSERT INTO `tables` VALUES (1,2,'AVAILABLE',NULL),(2,2,'AVAILABLE',NULL),(3,3,'AVAILABLE',NULL),(4,3,'AVAILABLE',NULL),(5,4,'AVAILABLE',NULL),(6,4,'AVAILABLE',NULL),(7,5,'AVAILABLE',NULL),(8,5,'AVAILABLE',NULL),(9,6,'AVAILABLE',NULL),(10,6,'AVAILABLE',NULL);
/*!40000 ALTER TABLE `tables` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `visit`
--

DROP TABLE IF EXISTS `visit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `visit` (
  `visitID` int NOT NULL AUTO_INCREMENT,
  `order_number` varchar(50) DEFAULT NULL,
  `confirmation_code` varchar(20) DEFAULT NULL,
  `tableID` int DEFAULT NULL,
  `startTime` datetime DEFAULT NULL,
  `endTime` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `subId` int DEFAULT NULL,
  PRIMARY KEY (`visitID`),
  KEY `idx_visit_order_number` (`order_number`),
  KEY `idx_visit_confirmation_code` (`confirmation_code`),
  KEY `idx_visit_tableID` (`tableID`),
  KEY `idx_visit_startTime` (`startTime`),
  CONSTRAINT `fk_visit_confirmation_code` FOREIGN KEY (`confirmation_code`) REFERENCES `orders` (`confirmation_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_visit_order_number` FOREIGN KEY (`order_number`) REFERENCES `orders` (`order_number`) ON DELETE SET NULL,
  CONSTRAINT `visit_ibfk_3` FOREIGN KEY (`tableID`) REFERENCES `tables` (`tableID`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=267 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `visit`
--

LOCK TABLES `visit` WRITE;
/*!40000 ALTER TABLE `visit` DISABLE KEYS */;
INSERT INTO `visit` VALUES (219,'DEC-ON1','DEC-ON1',NULL,'2025-12-05 18:00:00','2025-12-05 20:30:00','2026-01-14 16:10:08',NULL),(220,'DEC-L14-1','DEC-L14-1',3,'2025-12-07 18:10:00','2025-12-07 19:40:00','2026-01-14 16:10:08',NULL),(221,'DEC-L14-2','DEC-L14-2',4,'2025-12-08 18:40:00','2025-12-08 21:10:00','2026-01-14 16:10:08',NULL),(222,'DEC-WL-SEATED1','DEC-WL-SEATED1',NULL,'2025-12-05 19:00:00','2025-12-05 21:00:00','2026-01-14 16:51:46',NULL),(223,'DEC-WL-SEATED2','DEC-WL-SEATED2',6,'2025-12-08 19:30:00','2025-12-08 21:30:00','2026-01-14 16:51:46',NULL),(224,'DEC-WL-SEATED3','DEC-WL-SEATED3',7,'2025-12-10 20:00:00','2025-12-10 22:00:00','2026-01-14 16:51:46',NULL),(225,'DEC-WL-SEATED4','DEC-WL-SEATED4',8,'2025-12-12 20:30:00','2025-12-12 22:30:00','2026-01-14 16:51:46',NULL);
/*!40000 ALTER TABLE `visit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `waitingentry`
--

DROP TABLE IF EXISTS `waitingentry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `waitingentry` (
  `waitingID` int NOT NULL AUTO_INCREMENT,
  `number_of_guests` int NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `date` date NOT NULL,
  `status` varchar(20) DEFAULT 'WAITING',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `confirmation_code` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`waitingID`),
  UNIQUE KEY `confirmation_code` (`confirmation_code`),
  KEY `idx_waiting_date` (`date`),
  KEY `idx_waiting_status` (`status`),
  KEY `idx_waiting_confirmation_code` (`confirmation_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `waitingentry`
--

LOCK TABLES `waitingentry` WRITE;
/*!40000 ALTER TABLE `waitingentry` DISABLE KEYS */;
/*!40000 ALTER TABLE `waitingentry` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-01-17 23:16:13
