-- phpMyAdmin SQL Dump
-- version 4.0.6deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Erstellungszeit: 12. Mrz 2014 um 12:30
-- Server Version: 5.5.35-0ubuntu0.13.10.2
-- PHP-Version: 5.5.3-1ubuntu2.2

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+01:00";

--
-- Datenbank: `log4j2`
--

CREATE DATABASE IF NOT EXISTS `citstorm` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `citstorm`;

-- --------------------------------------------------------

--
-- Tabellenstruktur f�r Tabelle `citstorm`
--

CREATE TABLE IF NOT EXISTS `log4j2` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `datetime` datetime NOT NULL,
  `milliseconds` int(11) NOT NULL,
  `logger` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `level` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `exception` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `thread` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `marker` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `session` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=1 ;

-- Rechte f�r `log4j2`@`%`

GRANT USAGE ON *.* TO 'log4j2'@'%' IDENTIFIED BY PASSWORD '*69D4DBA2A0BEF21C2ADD1F483802230BF71C129E';

GRANT ALL PRIVILEGES ON `citstorm`.* TO 'log4j2'@'%';
