CREATE DATABASE IF NOT EXISTS `prototype_reservation`;
CREATE DATABASE IF NOT EXISTS `flyway`;
CREATE USER IF NOT EXISTS 'temporary'@'%' IDENTIFIED BY 'temporary';
GRANT ALL PRIVILEGES ON `prototype_reservation`.* TO 'temporary'@'%';
GRANT ALL PRIVILEGES ON `flyway`.* TO 'temporary'@'%';
FLUSH PRIVILEGES;