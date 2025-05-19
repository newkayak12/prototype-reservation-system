CREATE DATABASE IF NOT EXISTS `prototype-reservation`;
CREATE USER IF NOT EXISTS 'temporary'@'%' IDENTIFIED BY 'temporary';
GRANT ALL PRIVILEGES ON `prototype-reservation`.* TO 'temporary'@'%';
FLUSH PRIVILEGES;