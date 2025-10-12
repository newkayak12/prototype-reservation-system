CREATE DATABASE IF NOT EXISTS `prototype_reservation` char set 'utf8mb4' collate 'utf8mb4_general_ci';;
CREATE USER IF NOT EXISTS 'temporary'@'%' IDENTIFIED BY 'temporary';
GRANT ALL PRIVILEGES ON `prototype_reservation`.* TO 'temporary'@'%';
FLUSH PRIVILEGES;