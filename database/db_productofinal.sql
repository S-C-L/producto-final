CREATE DATABASE IF NOT EXISTS db_productofinal
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_productofinal;

CREATE TABLE IF NOT EXISTS usuarios (
    id INT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    usuario VARCHAR(50) NOT NULL,
    password VARCHAR(128) NULL,
    email VARCHAR(100) NOT NULL,
    sexo ENUM('Femenino','Masculino','Otro') NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    ciudad VARCHAR(100) NOT NULL,
    pais VARCHAR(100) NOT NULL,
    foto LONGTEXT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuario (usuario),
    UNIQUE KEY uq_email (email)
) ENGINE=InnoDB;

ALTER TABLE usuarios MODIFY COLUMN foto LONGTEXT NULL;

CREATE TABLE IF NOT EXISTS albumes (
    id INT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_album_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fotos (
    id INT NOT NULL AUTO_INCREMENT,
    album_id INT NOT NULL,
    archivo_foto VARCHAR(255) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    fecha DATE NOT NULL,
    pais VARCHAR(100) NOT NULL,
    inapropiado TINYINT(1) NOT NULL DEFAULT 0,
    fecha_subida TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_foto_album FOREIGN KEY (album_id) REFERENCES albumes(id)
) ENGINE=InnoDB;

ALTER TABLE fotos ADD COLUMN IF NOT EXISTS inapropiado TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS fotos_imagenes (
    foto_id INT NOT NULL,
    contenido LONGBLOB NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    PRIMARY KEY (foto_id),
    CONSTRAINT fk_imagen_foto FOREIGN KEY (foto_id) REFERENCES fotos(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fotos_imagenes (
    foto_id INT NOT NULL,
    contenido LONGBLOB NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    PRIMARY KEY (foto_id),
    CONSTRAINT fk_imagen_foto FOREIGN KEY (foto_id) REFERENCES fotos(id) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE fotos_imagenes MODIFY COLUMN contenido LONGBLOB NOT NULL;

ALTER TABLE usuarios MODIFY COLUMN foto LONGTEXT NULL;

