package mx.aprendizaje.actividad2;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Properties;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;


@WebServlet(name = "Servlet", urlPatterns = {
    "/", "/index", "/login", "/buscar", "/resultados", "/detalle",
    "/crearAlbum", "/crear-album", "/subirFoto", "/marcarInapropiado", "/cerrarSesion",
    "/agregar-fotos", "/ver-foto", "/imagen", "/perfil", "/actualizar-album", "/ultimas-fotos"
})
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 32 * 1024 * 1024,
    maxRequestSize = 33 * 1024 * 1024
)
public class Servlet extends HttpServlet {
    private static final long MAX_CARGA = 32L * 1024 * 1024;
    private static final String AVISO_TAMANO = "No se pueden cargar las fotos, máximo 32MB";
    private final SecureRandom random = new SecureRandom();
    private String url;
    private String usuarioBD;
    private String claveBD;
    private String vistaLogin;
    private String vistaIndex;
    private String vistaBuscar;
    private String vistaResultados;
    private String vistaDetalle;
    private String vistaCrearAlbum;
    private String vistaAgregarFotos;
    private String vistaVerFoto;
    private String vistaPerfil;
    private String vistaActualizarAlbum;
    private String vistaUltimasFotos;

    @Override
    public void init() throws ServletException {
        Properties config = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("base-datos.properties")) {
            if (in == null) throw new IOException("Falta base-datos.properties");
            config.load(in);
            Class.forName("com.mysql.cj.jdbc.Driver");
            url = config.getProperty("db.url");
            usuarioBD = config.getProperty("db.usuario");
            claveBD = config.getProperty("db.clave", "");
            vistaLogin = leerVista("login.html");
            vistaIndex = leerVista("index.html");
            vistaBuscar = leerVista("buscar.html");
            vistaResultados = leerVista("resultados.html");
            vistaDetalle = leerVista("detalle.html");
            vistaCrearAlbum = leerVista("crearAlbum.html");
            vistaAgregarFotos = leerVista("agregarFotos.html");
            vistaVerFoto = leerVista("verFoto.html");
            vistaPerfil = leerVista("perfil.html");
            vistaActualizarAlbum = leerVista("actualizarAlbum.html");
            vistaUltimasFotos = leerVista("ultimasFotos.html");
        } catch (IOException | ClassNotFoundException e) {
            throw new ServletException("No se pudo configurar la base de datos", e);
        }
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuarioBD, claveBD);
    }

    private String leerVista(String nombre) throws IOException {
        try (InputStream in = getServletContext().getResourceAsStream("/WEB-INF/vistas/" + nombre)) {
            if (in == null) throw new IOException("Falta la vista " + nombre);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        preparar(req, resp);
        HttpSession sesion = req.getSession();
        token(sesion);
        boolean autenticado = sesion.getAttribute("usuarioId") != null;
        String path = req.getServletPath();
        String ctx = req.getContextPath();

        Object aviso = sesion.getAttribute("aviso");
        sesion.removeAttribute("aviso");
        String avisoHtml = aviso == null ? "" : "<p class='aviso' role='status'>" + html(aviso) + "</p>";

        switch (path) {
            case "/imagen":
                try (Connection c = conectar()) {
                    int fotoId = identificador(req, "id");
                    if (!autenticado) {
                        resp.sendError(404);
                        return;
                    }
                    servirImagen(c, fotoId, resp);
                } catch (IllegalArgumentException e) {
                    resp.sendError(404);
                } catch (SQLException e) {
                    log("Error al cargar imagen", e);
                    resp.sendError(500, "No se pudo cargar la imagen.");
                }
                return;
            case "/ultimas-fotos":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                try (Connection c = conectar()) {
                    String fotos = renderUltimasFotos(c, ctx, sesion);
                    if (fotos.isEmpty()) {
                        resp.sendRedirect(ctx + "/index");
                        return;
                    }
                    resp.getWriter().print(vistaUltimasFotos
                            .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                            .replace("{{CONTEXTO}}", html(ctx))
                            .replace("{{AVISO}}", avisoHtml)
                            .replace("{{ULTIMAS_FOTOS}}", fotos));
                } catch (SQLException e) {
                    log("Error al consultar las últimas fotos", e);
                    resp.sendRedirect(ctx + "/index");
                }
                return;
            case "/perfil":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                try (Connection c = conectar()) {
                    resp.getWriter().print(renderPerfil(c, sesion)
                            .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                            .replace("{{CSRF}}", html(token(sesion)))
                            .replace("{{CONTEXTO}}", html(ctx))
                            .replace("{{AVISO}}", avisoHtml));
                } catch (SQLException e) {
                    log("Error al consultar el perfil", e);
                    resp.sendError(500, "No se pudo cargar tu información.");
                }
                return;
            case "/agregar-fotos":
            case "/actualizar-album":
            case "/ver-foto":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                try (Connection c = conectar()) {
                    String pagina = switch (path) {
                        case "/agregar-fotos" -> renderAgregarFotos(c, req, sesion, ctx);
                        case "/actualizar-album" -> renderActualizarAlbum(c, req, sesion);
                        default -> renderVerFoto(c, req, sesion, ctx);
                    };
                    resp.getWriter().print(pagina
                            .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                            .replace("{{CSRF}}", html(token(sesion)))
                            .replace("{{CONTEXTO}}", html(ctx))
                            .replace("{{AVISO}}", avisoHtml));
                } catch (IllegalArgumentException e) {
                    resp.sendError(404, e.getMessage());
                } catch (SQLException e) {
                    log("Error al consultar fotos", e);
                    resp.sendError(500, "No se pudieron cargar las fotos.");
                }
                return;
            case "/":
            case "/index":
                if (!autenticado) {
                    resp.getWriter().print(renderAcceso(req, sesion, avisoHtml));
                    return;
                }
                String nombre = (String) sesion.getAttribute("nombre");
                String formCerrar = formularioCerrar(ctx, sesion);
                String contenido;
                try (Connection c = conectar()) {
                    contenido = renderListaAlbumes(ctx, sesion, c);
                } catch (SQLException e) {
                    log("Error al consultar álbumes", e);
                    contenido = "<p class='aviso'>No se pudieron cargar los álbumes: " + html(e.getMessage()) + "</p>";
                }
                resp.getWriter().print(vistaIndex
                        .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                        .replace("{{CSRF}}", html(token(sesion)))
                        .replace("{{CONTEXTO}}", html(ctx))
                        .replace("{{AVISO}}", avisoHtml)
                        .replace("{{NOMBRE}}", html(nombreCorto(nombre)))
                        .replace("{{FOTO_USUARIO}}", html(fotoPerfilSrc((String) sesion.getAttribute("foto"))))
                        .replace("{{FORMULARIO_CERRAR}}", formCerrar)
                        .replace("{{CONTENIDO}}", contenido)
                        .replace("{{ALERTA}}", ""));
                return;
            case "/login":
                if (autenticado) {
                    resp.sendRedirect(ctx + "/index");
                    return;
                }
                resp.getWriter().print(renderAcceso(req, sesion, avisoHtml));
                return;
            case "/buscar":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                resp.getWriter().print(vistaBuscar
                        .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                        .replace("{{CSRF}}", html(token(sesion)))
                        .replace("{{CONTEXTO}}", html(ctx))
                        .replace("{{RUTA}}", html(ctx + "/resultados"))
                        .replace("{{AVISO}}", avisoHtml));
                return;
            case "/resultados":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                resp.getWriter().print(vistaResultados
                        .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                        .replace("{{CSRF}}", html(token(sesion)))
                        .replace("{{CONTEXTO}}", html(ctx))
                        .replace("{{RUTA_BUSCAR}}", html(ctx + "/buscar"))
                        .replace("{{AVISO}}", avisoHtml)
                        .replace("{{CONTENIDO}}", renderResultadosBusqueda(req, sesion, ctx))
                        .replace("{{BUSQUEDA}}", html(req.getParameter("q")))
                        .replace("{{RESUMEN_BUSQUEDA}}", req.getParameter("q") != null
                                ? "Búsqueda: " + html(req.getParameter("q"))
                                : "Título: " + html(req.getParameter("t")) + " · Fecha: " + html(req.getParameter("f")) + " · País: " + html(req.getParameter("p")))
                        .replace("{{TITULO_BUSQUEDA}}", html(req.getParameter("t") == null ? "" : req.getParameter("t")))
                        .replace("{{FECHA_BUSQUEDA}}", html(req.getParameter("f") == null ? "" : req.getParameter("f")))
                        .replace("{{PAIS_BUSQUEDA}}", html(req.getParameter("p") == null ? "" : req.getParameter("p"))));
                return;
            case "/detalle":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                String idParam = req.getParameter("id");
                if (idParam == null || idParam.isBlank()) {
                    sesion.setAttribute("aviso", "ID de foto inválido.");
                    resp.sendRedirect(ctx + "/index");
                    return;
                }
                try (Connection c = conectar()) {
                    resp.getWriter().print(vistaDetalle
                            .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                            .replace("{{CSRF}}", html(token(sesion)))
                            .replace("{{CONTEXTO}}", html(ctx))
                            .replace("{{AVISO}}", avisoHtml)
                            .replace("{{CONTENIDO}}", renderDetalleFoto(c, idParam, ctx)));
                } catch (SQLException e) {
                    log("Error al cargar detalle", e);
                    sesion.setAttribute("aviso", "No se pudo cargar la foto.");
                    resp.sendRedirect(ctx + "/index");
                }
                return;
            case "/cerrarSesion":
                sesion.invalidate();
                resp.sendRedirect(ctx + "/login");
                return;
            case "/crear-album":
                if (!autenticado) {
                    resp.sendRedirect(ctx + "/login");
                    return;
                }
                resp.getWriter().print(vistaCrearAlbum
                        .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                        .replace("{{CSRF}}", html(token(sesion)))
                        .replace("{{CONTEXTO}}", html(ctx))
                        .replace("{{AVISO}}", avisoHtml)
                        .replace("{{ALERTA}}", ""));
                return;
            default:
                resp.sendError(404, "Página no encontrada.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        preparar(req, resp);
        HttpSession sesion = req.getSession();
        String recibido;
        try {
            // Forzar el parseo para detectar exceso de tamaño antes de validar CSRF.
            if (req.getContentType() != null && req.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
                req.getParts();
            }
            recibido = req.getParameter("csrf");
        } catch (IllegalStateException e) {
            resp.setStatus(413);
            resp.getWriter().print("<!DOCTYPE html><html lang='es'><meta charset='UTF-8'>"
                    + "<title>Carga demasiado grande</title><p role='alert'>" + AVISO_TAMANO
                    + "</p><a href='" + html(req.getContextPath()) + "/index'>Volver a mis álbumes</a></html>");
            return;
        } catch (ServletException e) {
            resp.sendError(400, "No se pudo leer el formulario de carga.");
            return;
        }
        if (recibido == null || !recibido.equals(sesion.getAttribute("csrf"))) {
            resp.sendError(403, "Formulario inválido. Recarga la página.");
            return;
        }
        String accion = req.getParameter("accion");
        String ctx = req.getContextPath();
        String destino = ctx + "/index";
        try {
            if ("entrar".equals(accion)) {
                entrar(req);
                destino = ctx + "/ultimas-fotos";
            } else if ("actualizarPerfil".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                actualizarPerfil(req, sesion);
                sesion.setAttribute("aviso", "Tu información se actualizó correctamente.");
                destino = ctx + "/perfil";
            } else if ("registro".equals(accion)) {
                registrar(req);
                sesion.setAttribute("aviso", "Registro completado. Ya puedes iniciar sesión.");
                destino = ctx + "/login";
            } else if ("buscar".equals(accion)) {
                String titulo = req.getParameter("titulo");
                String fecha = req.getParameter("fecha");
                String pais = req.getParameter("pais");
                destino = ctx + "/resultados?t="
                        + java.net.URLEncoder.encode(titulo == null ? "" : titulo, StandardCharsets.UTF_8)
                        + "&f=" + java.net.URLEncoder.encode(fecha == null ? "" : fecha, StandardCharsets.UTF_8)
                        + "&p=" + java.net.URLEncoder.encode(pais == null ? "" : pais, StandardCharsets.UTF_8);
                resp.sendRedirect(destino);
                return;
            } else if ("crearAlbum".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                crearAlbum(req, sesion);
                sesion.setAttribute("aviso", "Álbum creado correctamente.");
                destino = ctx + "/index";
            } else if ("actualizarAlbum".equals(accion) || "eliminarAlbum".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                int albumId = modificarAlbum(req, sesion, "eliminarAlbum".equals(accion));
                sesion.setAttribute("aviso", "eliminarAlbum".equals(accion) ? "Álbum eliminado correctamente." : "Álbum actualizado correctamente.");
                destino = "eliminarAlbum".equals(accion) ? ctx + "/index" : ctx + "/agregar-fotos?id=" + albumId;
            } else if ("subirFoto".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                subirFoto(req, sesion);
                sesion.setAttribute("aviso", "Foto subida correctamente.");
                destino = ctx + "/index";
            } else if ("agregarFotos".equals(accion) || "cambiarImagen".equals(accion) || "eliminarImagen".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                int albumId = gestionarImagenes(req, sesion, accion);
                sesion.setAttribute("aviso", "agregarFotos".equals(accion) ? "Fotos guardadas correctamente."
                        : "cambiarImagen".equals(accion) ? "Imagen cambiada correctamente." : "Imagen eliminada.");
                destino = "cambiarImagen".equals(accion)
                        ? ctx + "/ver-foto?id=" + identificador(req, "fotoId")
                        : ctx + "/agregar-fotos?id=" + albumId;
            } else if ("marcarInapropiado".equals(accion)) {
                if (!autenticado(sesion)) throw new IllegalArgumentException("Debes iniciar sesión.");
                marcarInapropiadoFoto(req, sesion);
                sesion.setAttribute("aviso", "Foto marcada como inapropiada.");
                destino = ctx + "/index";
            } else if ("cerrarSesion".equals(accion)) {
                sesion.invalidate();
                destino = ctx + "/login";
            } else {
                throw new IllegalArgumentException("Acción no válida.");
            }
        } catch (IllegalArgumentException e) {
            sesion.setAttribute("aviso", e.getMessage());
            if ("registro".equals(accion) || "entrar".equals(accion)) {
                destino = ctx + "/login";
            } else if (!"cerrarSesion".equals(accion)) {
                destino = ctx + "/index";
            }
        } catch (SQLException e) {
            log("Error de base de datos en registro", e);
            String mensaje = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            sesion.setAttribute("aviso", mensaje.contains("packet") && (mensaje.contains("large") || mensaje.contains("max_allowed_packet"))
                    ? AVISO_TAMANO : "No se pudo completar la operación en la base de datos.");
            if ("registro".equals(accion) || "entrar".equals(accion)) {
                destino = ctx + "/login";
            } else if (!"cerrarSesion".equals(accion)) {
                destino = ctx + "/index";
            }
        } catch (Exception e) {
            log("Error en operación", e);
            sesion.setAttribute("aviso", "No se pudo completar la operación: " + e.getMessage());
            if ("registro".equals(accion) || "entrar".equals(accion)) {
                destino = ctx + "/login";
            } else {
                destino = ctx + "/index";
            }
        }
        if (destino.equals(ctx + "/index")) {
            if ("actualizarPerfil".equals(accion)) destino = ctx + (autenticado(sesion) ? "/perfil" : "/login");
            try {
                if ("agregarFotos".equals(accion)) destino = ctx + "/agregar-fotos?id=" + identificador(req, "album_id");
                if ("actualizarAlbum".equals(accion)) destino = ctx + "/actualizar-album?id=" + identificador(req, "album_id");
                if ("cambiarImagen".equals(accion) || "eliminarImagen".equals(accion)) destino = ctx + "/ver-foto?id=" + identificador(req, "fotoId");
            } catch (IllegalArgumentException e) {
                // Si no hay un identificador válido, volver al inicio.
            }
        }
        resp.setStatus(HttpServletResponse.SC_SEE_OTHER);
        resp.setHeader("Location", destino);
    }

    private boolean autenticado(HttpSession sesion) {
        return sesion.getAttribute("usuarioId") != null;
    }

    private void entrar(HttpServletRequest req) throws Exception {
        String usuario = req.getParameter("usuario");
        String clave = req.getParameter("clave");
        if (usuario == null || usuario.isBlank() || usuario.trim().length() > 50
                || clave == null || clave.isEmpty() || clave.length() > 128) {
            throw new IllegalArgumentException("Datos incorrectos.");
        }
        usuario = usuario.trim();
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement("SELECT id,nombre,foto,password FROM usuarios WHERE usuario=?")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Datos incorrectos.");
                String guardada = rs.getString("password");
                boolean valida = guardada != null && clave.equals(guardada);
                if (!valida) throw new IllegalArgumentException("Datos incorrectos.");
                req.getSession().invalidate();
                HttpSession nueva = req.getSession(true);
                nueva.setAttribute("usuarioId", rs.getInt("id"));
                nueva.setAttribute("nombre", rs.getString("nombre"));
                nueva.setAttribute("foto", rs.getString("foto"));
                token(nueva);
            }
        }
    }

    private void registrar(HttpServletRequest req) throws Exception {
        String nombre = texto(req, "nombre", 100);
        String usuario = texto(req, "usuario", 50);
        if (!usuario.matches("[A-Za-z0-9_.-]{3,50}")) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        }
        String clave = req.getParameter("clave");
        String claveRepetir = req.getParameter("clave_repetir");
        if (clave == null || clave.length() < 8 || clave.length() > 128) {
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 128 caracteres.");
        }
        if (claveRepetir == null || !clave.equals(claveRepetir)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }
        String email = texto(req, "email", 100);
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Email no válido.");
        }
        String sexo = texto(req, "sexo", 20);
        String sexoLimpio = sexo.trim();
        if (!sexoLimpio.equals("Femenino") && !sexoLimpio.equals("Masculino") && !sexoLimpio.equals("Otro")) {
            throw new IllegalArgumentException("Sexo inválido. Selecciona Femenino, Masculino u Otro.");
        }
        String fechaNacimiento = texto(req, "fecha_nacimiento", 10);
        String ciudad = texto(req, "ciudad", 100);
        String pais = texto(req, "pais", 100);

        String fotoBase64 = null;
        try {
            Part parteFoto = req.getPart("foto");
            if (parteFoto == null || parteFoto.getSize() == 0) {
                throw new IllegalArgumentException("Debes seleccionar una fotografía de perfil para registrarte.");
            }
            fotoBase64 = Base64.getEncoder().encodeToString(leerImagen(parteFoto).contenido());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al subir la foto de perfil.");
        }

        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO usuarios (nombre,usuario,password,email,sexo,fecha_nacimiento,ciudad,pais,foto) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, nombre);
            ps.setString(2, usuario);
            ps.setString(3, clave);
            ps.setString(4, email);
            ps.setString(5, sexoLimpio);
            ps.setString(6, fechaNacimiento);
            ps.setString(7, ciudad);
            ps.setString(8, pais);
            ps.setString(9, fotoBase64);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                if (e.getMessage() != null && e.getMessage().contains("uq_email")) {
                    throw new IllegalArgumentException("Ese email ya está registrado.");
                }
                throw new IllegalArgumentException("Ese nombre de usuario ya está registrado.");
            }
            throw e;
        }
    }

    private String fotoPerfilSrc(String foto) {
        if (foto == null || foto.isBlank()) {
            String avatar = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120' viewBox='0 0 120 120'>"
                    + "<rect width='120' height='120' fill='#e0f0ff'/><circle cx='60' cy='42' r='22' fill='#0080ff'/>"
                    + "<path d='M18 120v-15a42 42 0 0 1 84 0v15' fill='#0080ff'/></svg>";
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(avatar.getBytes(StandardCharsets.UTF_8));
        }
        String tipo = foto.startsWith("iVBOR") ? "png" : foto.startsWith("R0lGOD") ? "gif"
                : foto.startsWith("UklGR") ? "webp" : "jpeg";
        return "data:image/" + tipo + ";base64," + foto;
    }

    private String renderPerfil(Connection c, HttpSession sesion) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT nombre,usuario,email,sexo,fecha_nacimiento,ciudad,pais,foto FROM usuarios WHERE id=?")) {
            ps.setInt(1, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El usuario de la sesión ya no existe.");
                String pagina = vistaPerfil;
                for (String campo : List.of("nombre", "usuario", "email", "fecha_nacimiento", "ciudad", "pais")) {
                    pagina = pagina.replace("{{" + campo.toUpperCase(Locale.ROOT) + "}}", html(rs.getString(campo)));
                }
                pagina = pagina.replace("{{FOTO}}", html(fotoPerfilSrc(rs.getString("foto"))));
                for (String sexo : List.of("Femenino", "Masculino", "Otro")) {
                    pagina = pagina.replace("{{" + sexo.toUpperCase(Locale.ROOT) + "}}", sexo.equals(rs.getString("sexo")) ? "selected" : "");
                }
                return pagina;
            }
        }
    }

    private void actualizarPerfil(HttpServletRequest req, HttpSession sesion) throws Exception {
        String nombre = texto(req, "nombre", 100);
        String usuario = texto(req, "usuario", 50);
        if (!usuario.matches("[A-Za-z0-9_.-]{3,50}")) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        }
        String email = texto(req, "email", 100);
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) throw new IllegalArgumentException("Email no válido.");
        String sexo = texto(req, "sexo", 20);
        if (!List.of("Femenino", "Masculino", "Otro").contains(sexo)) throw new IllegalArgumentException("Selecciona un sexo válido.");
        String fecha = texto(req, "fecha_nacimiento", 10);
        try {
            if (LocalDate.parse(fecha).isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de nacimiento no puede estar en el futuro.");
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha de nacimiento no válida.");
        }
        String ciudad = texto(req, "ciudad", 100);
        String pais = texto(req, "pais", 100);
        String clave = req.getParameter("clave");
        String repetir = req.getParameter("clave_repetir");
        if (clave == null || clave.isEmpty()) {
            clave = null;
            if (repetir != null && !repetir.isEmpty()) throw new IllegalArgumentException("Escribe la nueva contraseña en ambos campos.");
        } else {
            if (clave.length() < 8 || clave.length() > 128) throw new IllegalArgumentException("La contraseña debe tener entre 8 y 128 caracteres.");
            if (!clave.equals(repetir)) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }
        String foto = null;
        Part parte = req.getPart("foto");
        if (parte != null && parte.getSize() > 0) foto = Base64.getEncoder().encodeToString(leerImagen(parte).contenido());
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(
                "UPDATE usuarios SET nombre=?,usuario=?,email=?,sexo=?,fecha_nacimiento=?,ciudad=?,pais=?,"
                + "password=COALESCE(?,password),foto=COALESCE(?,foto) WHERE id=?")) {
            ps.setString(1, nombre);
            ps.setString(2, usuario);
            ps.setString(3, email);
            ps.setString(4, sexo);
            ps.setString(5, fecha);
            ps.setString(6, ciudad);
            ps.setString(7, pais);
            ps.setString(8, clave);
            ps.setString(9, foto);
            ps.setInt(10, (Integer) sesion.getAttribute("usuarioId"));
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new IllegalArgumentException(e.getMessage() != null && e.getMessage().contains("uq_email")
                        ? "Ese email ya está registrado." : "Ese nombre de usuario ya está registrado.");
            }
            throw e;
        }
        sesion.setAttribute("nombre", nombre);
        if (foto != null) sesion.setAttribute("foto", foto);
    }

    private void crearAlbum(HttpServletRequest req, HttpSession sesion) throws Exception {
        String titulo = texto(req, "titulo_album", 150);
        String descripcion = texto(req, "descripcion_album", 5000);
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO albumes (usuario_id,titulo,descripcion) VALUES (?,?,?)")) {
            ps.setInt(1, (Integer) sesion.getAttribute("usuarioId"));
            ps.setString(2, titulo);
            ps.setString(3, descripcion);
            ps.executeUpdate();
        }
    }

    private int modificarAlbum(HttpServletRequest req, HttpSession sesion, boolean eliminar) throws SQLException {
        int albumId = identificador(req, "album_id");
        String titulo = eliminar ? null : texto(req, "titulo_album", 150);
        String descripcion = eliminar ? null : texto(req, "descripcion_album", 5000);
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM albumes WHERE id=? AND usuario_id=? FOR UPDATE")) {
                    ps.setInt(1, albumId);
                    ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("El álbum no existe o no te pertenece.");
                    }
                }
                if (eliminar) {
                    // fotos_imagenes se elimina por la clave foránea ON DELETE CASCADE.
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM fotos WHERE album_id=?")) {
                        ps.setInt(1, albumId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM albumes WHERE id=? AND usuario_id=?")) {
                        ps.setInt(1, albumId);
                        ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE albumes SET titulo=?,descripcion=? WHERE id=? AND usuario_id=?")) {
                        ps.setString(1, titulo);
                        ps.setString(2, descripcion);
                        ps.setInt(3, albumId);
                        ps.setInt(4, (Integer) sesion.getAttribute("usuarioId"));
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return albumId;
            } catch (SQLException | IllegalArgumentException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void subirFoto(HttpServletRequest req, HttpSession sesion) throws Exception {
        String albumIdStr = req.getParameter("album_id");
        int albumId;
        try {
            albumId = Integer.parseInt(albumIdStr);
            if (albumId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Álbum inválido.");
        }
        String titulo = texto(req, "titulo_foto", 150);
        String fecha = texto(req, "fecha_foto", 10);
        String pais = texto(req, "pais_foto", 100);

        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM albumes WHERE id=? AND usuario_id=?")) {
            ps.setInt(1, albumId);
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("El álbum no existe o no te pertenece.");
            }
        }

        String archivoNombre = null;
        try {
            Part parteFoto = req.getPart("archivo_foto");
            if (parteFoto == null || parteFoto.getSize() == 0) {
                throw new IllegalArgumentException("Debe seleccionar una imagen.");
            }
            String nombreArchivo = Paths.get(parteFoto.getSubmittedFileName()).getFileName().toString();
            archivoNombre = System.currentTimeMillis() + "_" + nombreArchivo;
            String uploadDir = getServletContext().getRealPath("/uploads");
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();
            parteFoto.write(uploadDir + File.separator + archivoNombre);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al subir la imagen.");
        }

        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO fotos (album_id,archivo_foto,titulo,fecha,pais) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, albumId);
            ps.setString(2, archivoNombre);
            ps.setString(3, titulo);
            ps.setString(4, fecha);
            ps.setString(5, pais);
            ps.executeUpdate();
        }
    }

    private void marcarInapropiadoFoto(HttpServletRequest req, HttpSession sesion) throws Exception {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE fotos f JOIN albumes a ON a.id=f.album_id "
                     + "SET f.inapropiado=1 WHERE f.id=? AND a.usuario_id=? AND f.inapropiado=0")) {
            ps.setInt(1, identificador(req, "fotoId"));
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("No puedes marcar esta foto: solo el creador del álbum puede hacerlo, y debe estar sin marcar.");
            }
        }
    }

    private String formularioCerrar(String ctx, HttpSession sesion) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<form method='post' action='" + html(ctx + "/cerrarSesion") + "'>");
        out.println("<input type='hidden' name='accion' value='cerrarSesion'>");
        out.println("<input type='hidden' name='csrf' value='" + html(token(sesion)) + "'>");
        out.println("<button class='logout-button' type='submit'>Cerrar sesión</button>");
        out.println("</form>");
        out.flush();
        return sw.toString();
    }

    private String renderMenu(HttpServletRequest req, boolean autenticado) {
        String ctx = req.getContextPath();
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class='menu-bar'>");
        sb.append("<a href='").append(html(ctx)).append("/index'>Inicio</a>");
        if (autenticado) {
            sb.append("<a href='").append(html(ctx)).append("/buscar'>Buscar fotos</a>");
            sb.append("<a href='").append(html(ctx)).append("/cerrarSesion'>Cerrar sesión</a>");
        } else {
            sb.append("<a href='").append(html(ctx)).append("/login'>Iniciar sesión</a>");
        }
        sb.append("</nav>");
        return sb.toString();
    }

    private String renderAcceso(HttpServletRequest req, HttpSession sesion, String avisoHtml) {
        return vistaLogin.replace("{{RUTA}}", html(req.getContextPath() + "/login"))
                .replace("{{CONTEXTO}}", html(req.getContextPath()))
                .replace("{{CSRF}}", html(token(sesion)))
                .replace("{{NONCE}}", html(req.getAttribute("nonce")))
                .replace("{{AVISO}}", avisoHtml);
    }

    private String renderUltimasFotos(Connection c, String ctx, HttpSession sesion) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT f.id,f.titulo,f.fecha,f.pais FROM fotos f JOIN albumes a ON a.id=f.album_id "
                + "WHERE a.usuario_id=? AND f.inapropiado=0 ORDER BY f.fecha_subida DESC,f.id DESC LIMIT 5")) {
            ps.setInt(1, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append("<article class='foto-reciente'><img loading='lazy' src='").append(html(ctx))
                            .append("/imagen?id=").append(rs.getInt("id")).append("' alt='").append(html(rs.getString("titulo"))).append("'>");
                    sb.append("<h3>").append(html(rs.getString("titulo"))).append("</h3><p>Fecha: ")
                            .append(html(rs.getDate("fecha"))).append("<br>País: ").append(html(rs.getString("pais"))).append("</p></article>");
                }
            }
        }
        return sb.length() == 0 ? "" : "<div class='fotos-recientes'>" + sb + "</div>";
    }

    private String renderListaAlbumes(String ctx, HttpSession sesion, Connection c) throws SQLException {
        StringBuilder sb = new StringBuilder();
        boolean hayAlbumes = false;
        sb.append("<p class='busqueda-avanzada'><a href='").append(html(ctx))
                .append("/buscar'>Búsqueda avanzada por título, fecha y país</a></p>");
        sb.append("<p class='ultimas-fotos-enlace'><a href='").append(html(ctx))
                .append("/ultimas-fotos'>Visualizar mis últimas 5 fotografías</a></p>");
        sb.append("<div class='album-grid'>");
        sb.append("<a href='").append(html(ctx)).append("/crear-album' class='album-card crear-album'>");
        sb.append("<span class='album-cover'><span class='btn-icon' aria-hidden='true'>+</span></span>");
        sb.append("<span class='album-title'>Crear álbum</span></a>");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.id,a.titulo,"
                + "(SELECT COUNT(*) FROM fotos f WHERE f.album_id=a.id AND f.inapropiado=0) AS cantidad,"
                + "(SELECT f.id FROM fotos f WHERE f.album_id=a.id AND f.inapropiado=0 ORDER BY f.id LIMIT 1) AS portada "
                + "FROM albumes a WHERE a.usuario_id=? ORDER BY a.id DESC")) {
            ps.setInt(1, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append("<a class='album-card' href='").append(html(ctx)).append("/agregar-fotos?id=")
                            .append(rs.getInt("id")).append("'>");
                    hayAlbumes = true;
                    sb.append("<div class='album-cover'>");
                    String portada = rs.getString("portada");
                    if (portada != null && !portada.isBlank()) {
                        sb.append("<img src='").append(html(ctx)).append("/imagen?id=").append(html(portada))
                                .append("' alt='").append(html(rs.getString("titulo"))).append("'>");
                    }
                    sb.append("</div><h3 class='album-title'>").append(html(rs.getString("titulo"))).append("</h3>");
                    int cantidad = rs.getInt("cantidad");
                    sb.append("<p class='album-count'>").append(cantidad)
                            .append(cantidad == 1 ? " foto" : " fotos").append("</p></a>");
                }
            }
        }
        sb.append("</div>");
        if (hayAlbumes) {
            sb.insert(0, "<form class='busqueda' method='get' action='" + html(ctx) + "/resultados' role='search'>"
                    + "<input type='search' name='q' placeholder='Buscar álbum o foto' aria-label='Buscar álbum o foto' maxlength='150' required>"
                    + "<button type='submit'>Buscar</button></form>");
        }
        return sb.toString();
    }

    private void renderFotosAlbum(StringBuilder sb, String ctx, HttpSession sesion, Connection c, int albumId, boolean esCreador) throws SQLException {
        sb.append("<h4>Fotos del álbum</h4>");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT f.id,f.archivo_foto,f.titulo,f.fecha,f.pais,f.inapropiado,u.nombre,"
                + "CASE WHEN f.inapropiado=0 THEN f.archivo_foto ELSE NULL END AS archivo "
                + "FROM fotos f JOIN usuarios u ON u.id=(SELECT usuario_id FROM albumes WHERE id=f.album_id) "
                + "WHERE f.album_id=? ORDER BY f.id")) {
            ps.setInt(1, albumId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean hay = false;
                while (rs.next()) {
                    hay = true;
                    sb.append("<section class='forum-message'><h4>").append(html(rs.getString("titulo"))).append("</h4>");
                    sb.append("<p>Autor: ").append(html(rs.getString("nombre"))).append(" · Fecha: ")
                            .append(html(rs.getDate("fecha"))).append(" · País: ").append(html(rs.getString("pais"))).append("</p>");
                    if (rs.getBoolean("inapropiado")) {
                        sb.append("<p class='aviso'>Foto marcada como inapropiada. Imagen oculta.</p>");
                    } else {
                        String archivo = rs.getString("archivo");
                        if (archivo != null && !archivo.isBlank()) {
                            sb.append("<img src='").append(ctx).append("/uploads/").append(html(archivo))
                                    .append("' alt='").append(html(rs.getString("titulo")))
                                    .append("' class='foto-mini' style='max-width:200px; max-height:200px; margin-top:8px;'><br>");
                        }
                        if (esCreador) {
                            sb.append("<form method='post' action='").append(ctx).append("/index'>");
                            sb.append("<input type='hidden' name='accion' value='marcarInapropiado'>");
                            sb.append("<input type='hidden' name='csrf' value='").append(html(token(sesion))).append("'>");
                            sb.append("<input type='hidden' name='fotoId' value='").append(rs.getInt("id")).append("'>");
                            sb.append("<button type='submit'>Marcar como inapropiada</button></form>");
                        }
                    }
                    sb.append("</section>");
                }
                if (!hay) sb.append("<p>Todavía no hay fotos en este álbum.</p>");
            }
        }
        // Formulario subir foto
        sb.append("<details class='message-form'><summary>Subir una foto a este álbum</summary>");
        sb.append("<form method='post' action='").append(ctx).append("/index' enctype='multipart/form-data'>");
        sb.append("<input type='hidden' name='accion' value='subirFoto'>");
        sb.append("<input type='hidden' name='csrf' value='").append(html(token(sesion))).append("'>");
        sb.append("<input type='hidden' name='album_id' value='").append(albumId).append("'>");
        sb.append("<label>Título<input name='titulo_foto' maxlength='150' required></label>");
        sb.append("<label>Fecha<input type='date' name='fecha_foto' required></label>");
        sb.append("<label>País<input name='pais_foto' maxlength='100' required></label>");
        sb.append("<label>Imagen<input type='file' name='archivo_foto' accept='image/*' required></label>");
        sb.append("<button type='submit'>Subir foto</button></form></details>");
    }

    private String filtroTexto(String... campos) {
        return "(" + String.join(" OR ", java.util.Arrays.stream(campos)
                .map(campo -> campo + " LIKE ? ESCAPE '!'").toList()) + ")";
    }

    private void parametrosBusqueda(PreparedStatement ps, HttpSession sesion, String patron, int campos) throws SQLException {
        ps.setInt(1, (Integer) sesion.getAttribute("usuarioId"));
        for (int i = 0; i < campos; i++) ps.setString(i + 2, patron);
    }

    private String renderBusquedaGeneral(String consulta, HttpSession sesion, String ctx) {
        consulta = consulta.trim();
        if (consulta.isEmpty()) return "<p>Escribe un nombre, título, fecha o país para buscar.</p>";
        if (consulta.length() > 150) return "<p>La búsqueda admite hasta 150 caracteres.</p>";
        String patron = "%" + consulta.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String[] camposAlbum = {"a.titulo", "a.descripcion", "DATE_FORMAT(a.fecha_creacion,'%Y-%m-%d')",
                "DATE_FORMAT(a.fecha_creacion,'%d/%m/%Y')", "DATE_FORMAT(a.fecha_creacion,'%d-%m-%Y')"};
        String[] camposFoto = {"f.titulo", "f.archivo_foto", "f.pais", "DATE_FORMAT(f.fecha,'%Y-%m-%d')",
                "DATE_FORMAT(f.fecha,'%d/%m/%Y')", "DATE_FORMAT(f.fecha,'%d-%m-%Y')"};
        StringBuilder sb = new StringBuilder("<h2>Álbumes</h2>");
        try (Connection c = conectar()) {
            String sqlAlbumes = "SELECT a.id,a.titulo,a.descripcion,DATE(a.fecha_creacion) AS fecha,"
                    + "(SELECT f.id FROM fotos f WHERE f.album_id=a.id AND f.inapropiado=0 ORDER BY f.id LIMIT 1) AS portada,"
                    + "(SELECT GROUP_CONCAT(DISTINCT f.pais ORDER BY f.pais SEPARATOR ', ') FROM fotos f WHERE f.album_id=a.id AND f.inapropiado=0) AS pais "
                    + "FROM albumes a WHERE a.usuario_id=? AND (" + filtroTexto(camposAlbum)
                    + " OR EXISTS (SELECT 1 FROM fotos f WHERE f.album_id=a.id AND f.inapropiado=0 AND "
                    + filtroTexto(camposFoto) + ")) ORDER BY a.id DESC";
            try (PreparedStatement ps = c.prepareStatement(sqlAlbumes)) {
                parametrosBusqueda(ps, sesion, patron, camposAlbum.length + camposFoto.length);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean hay = false;
                    while (rs.next()) {
                        hay = true;
                        sb.append("<article class='foto-resumen'>");
                        int portada = rs.getInt("portada");
                        if (!rs.wasNull()) {
                            sb.append("<img class='foto-mini' src='").append(html(ctx)).append("/imagen?id=").append(portada)
                                    .append("' alt='Portada de ").append(html(rs.getString("titulo"))).append("'>");
                        } else {
                            sb.append("<div class='foto-mini sin-portada'>Sin fotos</div>");
                        }
                        sb.append("<div class='foto-info'><strong>").append(html(rs.getString("titulo"))).append("</strong>");
                        sb.append("<p>").append(multilinea(rs.getString("descripcion"))).append("</p>");
                        sb.append("<p>Fecha: ").append(html(rs.getDate("fecha"))).append("<br>País de las fotos: ")
                                .append(rs.getString("pais") == null ? "Sin fotos" : html(rs.getString("pais"))).append("</p></div>");
                        sb.append("<a class='btn' href='").append(html(ctx)).append("/agregar-fotos?id=")
                                .append(rs.getInt("id")).append("'>Abrir álbum</a></article>");
                    }
                    if (!hay) sb.append("<p>No se encontraron álbumes con esos criterios.</p>");
                }
            }
            sb.append("<h2>Fotos e imágenes</h2>");
            String sqlFotos = "SELECT f.id,f.titulo,f.archivo_foto,f.fecha,f.pais,a.titulo AS album "
                    + "FROM fotos f JOIN albumes a ON a.id=f.album_id WHERE a.usuario_id=? AND f.inapropiado=0 AND ("
                    + filtroTexto(camposFoto) + " OR " + filtroTexto("a.titulo", "a.descripcion") + ") ORDER BY f.id DESC";
            try (PreparedStatement ps = c.prepareStatement(sqlFotos)) {
                parametrosBusqueda(ps, sesion, patron, camposFoto.length + 2);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean hay = false;
                    while (rs.next()) {
                        hay = true;
                        sb.append("<article class='foto-resumen'><img class='foto-mini' src='").append(html(ctx))
                                .append("/imagen?id=").append(rs.getInt("id")).append("' alt='")
                                .append(html(rs.getString("titulo"))).append("'>");
                        sb.append("<div class='foto-info'><strong>").append(html(rs.getString("titulo"))).append("</strong>");
                        sb.append("<p>Archivo: ").append(html(rs.getString("archivo_foto")))
                                .append("<br>Fecha: ").append(html(rs.getDate("fecha")))
                                .append("<br>País: ").append(html(rs.getString("pais")))
                                .append("<br>Álbum: ").append(html(rs.getString("album"))).append("</p></div>");
                        sb.append("<a class='btn' href='").append(html(ctx)).append("/ver-foto?id=")
                                .append(rs.getInt("id")).append("'>Ver imagen</a></article>");
                    }
                    if (!hay) sb.append("<p>No se encontraron fotos con esos criterios.</p>");
                }
            }
        } catch (SQLException e) {
            log("Error en búsqueda de álbumes y fotos", e);
            return "<p class='aviso'>No se pudo realizar la búsqueda. Intenta de nuevo.</p>";
        }
        return sb.toString();
    }

    private String renderResultadosBusqueda(HttpServletRequest req, HttpSession sesion, String ctx) {
        if (req.getParameter("q") != null) return renderBusquedaGeneral(req.getParameter("q"), sesion, ctx);
        StringBuilder sb = new StringBuilder();
        String titulo = req.getParameter("t") == null ? "" : req.getParameter("t").trim();
        String fecha = req.getParameter("f") == null ? "" : req.getParameter("f").trim();
        String pais = req.getParameter("p") == null ? "" : req.getParameter("p").trim();
        if (titulo.isEmpty() && fecha.isEmpty() && pais.isEmpty()) {
            return "<p class='aviso'>Completa al menos un criterio: título, fecha o país.</p>";
        }
        if (titulo.length() > 150 || pais.length() > 100 || fecha.length() > 10) {
            return "<p class='aviso'>Los criterios de búsqueda superan la longitud permitida.</p>";
        }
        if (!fecha.isEmpty()) {
            try {
                LocalDate.parse(fecha);
            } catch (java.time.format.DateTimeParseException e) {
                return "<p class='aviso'>Selecciona una fecha válida.</p>";
            }
        }

        sb.append("<h2>Fotos encontradas</h2>");
        try (Connection c = conectar()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT f.id,f.archivo_foto,f.titulo,f.fecha,f.pais,u.nombre,a.titulo AS album "
                    + "FROM fotos f "
                    + "JOIN albumes a ON a.id=f.album_id "
                    + "JOIN usuarios u ON u.id=a.usuario_id "
                    + "WHERE f.inapropiado=0 AND a.usuario_id=?");
            if (titulo != null && !titulo.isBlank()) sql.append(" AND f.titulo LIKE ? ESCAPE '!'");
            if (fecha != null && !fecha.isBlank()) sql.append(" AND f.fecha = ?");
            if (pais != null && !pais.isBlank()) sql.append(" AND f.pais LIKE ? ESCAPE '!'");
            sql.append(" ORDER BY f.id DESC");

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int idx = 1;
                ps.setInt(idx++, (Integer) sesion.getAttribute("usuarioId"));
                if (titulo != null && !titulo.isBlank()) ps.setString(idx++, "%" + titulo.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
                if (fecha != null && !fecha.isBlank()) ps.setString(idx++, fecha);
                if (pais != null && !pais.isBlank()) ps.setString(idx++, "%" + pais.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");

                try (ResultSet rs = ps.executeQuery()) {
                    boolean hay = false;
                    while (rs.next()) {
                        hay = true;
                        sb.append("<div class='foto-resumen'>");
                        String archivo = rs.getString("archivo_foto");
                        if (archivo != null && !archivo.isBlank()) {
                            sb.append("<img src='").append(html(ctx)).append("/imagen?id=").append(rs.getInt("id"))
                                    .append("' alt='").append(html(rs.getString("titulo"))).append("' class='foto-mini'>");
                        }
                        sb.append("<div class='foto-info'>");
                        sb.append("<strong>").append(html(rs.getString("titulo"))).append("</strong><br>");
                        sb.append("Fecha: ").append(html(rs.getDate("fecha"))).append("<br>");
                        sb.append("País: ").append(html(rs.getString("pais"))).append("<br>");
                        sb.append("Álbum: ").append(html(rs.getString("album"))).append("<br>");
                        sb.append("Usuario: ").append(html(rs.getString("nombre")));
                        sb.append("</div>");
                        sb.append("<a class='btn' href='").append(html(ctx)).append("/ver-foto?id=")
                                .append(rs.getInt("id")).append("'>Ver detalle</a>");
                        sb.append("</div>");
                    }
                    if (!hay) sb.append("<p>No se encontraron fotos con esos criterios.</p>");
                }
            }
        } catch (SQLException e) {
            log("Error al buscar fotos", e);
            sb.append("<p>No se pudo realizar la búsqueda. Verifica la base de datos.</p>");
        }
        return sb.toString();
    }

    private String renderDetalleFoto(Connection c, String idParam, String ctx) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT f.id,f.archivo_foto,f.titulo,f.fecha,f.pais,"
                + " a.titulo AS album, u.nombre AS usuario "
                + "FROM fotos f "
                + "JOIN albumes a ON a.id=f.album_id "
                + "JOIN usuarios u ON u.id=a.usuario_id "
                + "WHERE f.id=? AND f.inapropiado=0")) {
            ps.setInt(1, Integer.parseInt(idParam));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "<p>Foto no encontrada.</p>";
                }
                sb.append("<div class='foto-detalle'>");
                String archivo = rs.getString("archivo_foto");
                if (archivo != null && !archivo.isBlank()) {
                    sb.append("<img src='").append(html(ctx)).append("/imagen?id=").append(rs.getInt("id"))
                            .append("' alt='").append(html(rs.getString("titulo"))).append("' class='foto-grande'>");
                }
                sb.append("<div class='detalle-info'>");
                sb.append("<strong>Título:</strong> ").append(html(rs.getString("titulo"))).append("<br>");
                sb.append("<strong>Fecha:</strong> ").append(html(rs.getDate("fecha"))).append("<br>");
                sb.append("<strong>País:</strong> ").append(html(rs.getString("pais"))).append("<br>");
                sb.append("<strong>Álbum:</strong> ").append(html(rs.getString("album"))).append("<br>");
                sb.append("<strong>Usuario:</strong> ").append(html(rs.getString("usuario")));
                sb.append("</div></div>");
            }
        }
        return sb.toString();
    }

    private String tituloAlbum(Connection c, int id, HttpSession sesion) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT titulo FROM albumes WHERE id=? AND usuario_id=?")) {
            ps.setInt(1, id);
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("El álbum no existe o no te pertenece.");
                return rs.getString("titulo");
            }
        }
    }

    private String renderAgregarFotos(Connection c, HttpServletRequest req, HttpSession sesion, String ctx) throws SQLException {
        int albumId = identificador(req, "id");
        String titulo = tituloAlbum(c, albumId, sesion);
        String descripcion = "";
        try (PreparedStatement ps = c.prepareStatement("SELECT descripcion FROM albumes WHERE id=? AND usuario_id=?")) {
            ps.setInt(1, albumId);
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) descripcion = rs.getString("descripcion");
            }
        }
        StringBuilder fotos = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,titulo FROM fotos WHERE album_id=? AND inapropiado=0 ORDER BY id DESC")) {
            ps.setInt(1, albumId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fotos.append("<a class='foto' href='").append(html(ctx)).append("/ver-foto?id=").append(rs.getInt("id"))
                            .append("'><img loading='lazy' src='").append(html(ctx)).append("/imagen?id=").append(rs.getInt("id"))
                            .append("' alt='").append(html(rs.getString("titulo"))).append("'></a>");
                }
            }
        }
        return vistaAgregarFotos.replace("{{ALBUM_ID}}", String.valueOf(albumId))
                .replace("{{TITULO}}", html(titulo)).replace("{{DESCRIPCION}}", multilinea(descripcion))
                .replace("{{FOTOS}}", fotos.toString());
    }

    private String renderActualizarAlbum(Connection c, HttpServletRequest req, HttpSession sesion) throws SQLException {
        int albumId = identificador(req, "id");
        try (PreparedStatement ps = c.prepareStatement("SELECT titulo,descripcion FROM albumes WHERE id=? AND usuario_id=?")) {
            ps.setInt(1, albumId);
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("El álbum no existe o no te pertenece.");
                return vistaActualizarAlbum.replace("{{ALBUM_ID}}", String.valueOf(albumId))
                        .replace("{{TITULO}}", html(rs.getString("titulo")))
                        .replace("{{DESCRIPCION_FORM}}", html(rs.getString("descripcion")));
            }
        }
    }

    private String renderVerFoto(Connection c, HttpServletRequest req, HttpSession sesion, String ctx) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT f.*,a.titulo AS album,u.nombre FROM fotos f JOIN albumes a ON a.id=f.album_id "
                + "JOIN usuarios u ON u.id=a.usuario_id WHERE f.id=? AND a.usuario_id=? AND f.inapropiado=0")) {
            ps.setInt(1, identificador(req, "id"));
            ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Foto no encontrada.");
                return vistaVerFoto.replace("{{FOTO_ID}}", String.valueOf(rs.getInt("id")))
                        .replace("{{ALBUM_ID}}", String.valueOf(rs.getInt("album_id")))
                        .replace("{{TITULO}}", html(rs.getString("titulo")))
                        .replace("{{ALBUM}}", html(rs.getString("album")))
                        .replace("{{AUTOR}}", html(rs.getString("nombre")))
                        .replace("{{FECHA}}", html(rs.getDate("fecha")))
                        .replace("{{PAIS}}", html(rs.getString("pais")));
            }
        }
    }

    private record ImagenNueva(byte[] contenido, String tipo, String nombre) {}

    private ImagenNueva leerImagen(Part parte) throws IOException {
        if (parte != null && parte.getSize() > MAX_CARGA) {
            throw new IllegalArgumentException(AVISO_TAMANO);
        }
        if (parte == null || parte.getSize() == 0) {
            throw new IllegalArgumentException("Selecciona al menos una imagen.");
        }
        String nombre = Paths.get(parte.getSubmittedFileName().replace('\\', '/')).getFileName().toString();
        if (!nombre.toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png|gif|webp)")) {
            throw new IllegalArgumentException("Formatos permitidos: JPG, JPEG, PNG, GIF y WebP.");
        }
        byte[] bytes;
        try (InputStream in = parte.getInputStream()) {
            bytes = in.readAllBytes();
        }
        String tipo;
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            tipo = "image/webp";
        } else {
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var lectores = ImageIO.getImageReaders(in);
                if (!lectores.hasNext()) throw new IllegalArgumentException("El archivo no es una imagen válida.");
                ImageReader lector = lectores.next();
                try {
                    lector.setInput(in);
                    String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                    tipo = switch (formato) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "gif" -> "image/gif";
                        default -> throw new IllegalArgumentException("Formato de imagen no permitido.");
                    };
                    if ((long) lector.getWidth(0) * lector.getHeight(0) > 40_000_000) {
                        throw new IllegalArgumentException("La imagen supera los 40 megapíxeles.");
                    }
                } finally {
                    lector.dispose();
                }
            }
        }
        return new ImagenNueva(bytes, tipo, nombre.substring(0, Math.min(nombre.length(), 150)));
    }

    private int gestionarImagenes(HttpServletRequest req, HttpSession sesion, String accion) throws Exception {
        List<ImagenNueva> imagenes = new ArrayList<>();
        boolean agregar = "agregarFotos".equals(accion);
        boolean cambiar = "cambiarImagen".equals(accion);
        if (agregar) {
            var partes = req.getParts();
            long total = 0;
            for (Part parte : partes) {
                if ("imagenes".equals(parte.getName())) total += parte.getSize();
            }
            if (total > MAX_CARGA) throw new IllegalArgumentException(AVISO_TAMANO);
            for (Part parte : partes) {
                if ("imagenes".equals(parte.getName()) && parte.getSize() > 0) imagenes.add(leerImagen(parte));
            }
            if (imagenes.isEmpty()) throw new IllegalArgumentException("Selecciona al menos una imagen.");
        } else if (cambiar) {
            imagenes.add(leerImagen(req.getPart("imagen")));
        }
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                int albumId;
                int fotoId = agregar ? 0 : identificador(req, "fotoId");
                if (agregar) {
                    albumId = identificador(req, "album_id");
                    tituloAlbum(c, albumId, sesion);
                } else {
                    try (PreparedStatement ps = c.prepareStatement("SELECT f.album_id FROM fotos f JOIN albumes a ON a.id=f.album_id WHERE f.id=? AND a.usuario_id=? FOR UPDATE")) {
                        ps.setInt(1, fotoId);
                        ps.setInt(2, (Integer) sesion.getAttribute("usuarioId"));
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("No puedes modificar esta foto.");
                            albumId = rs.getInt("album_id");
                        }
                    }
                }
                if (agregar || cambiar) {
                    for (ImagenNueva imagen : imagenes) {
                        if (agregar) {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "INSERT INTO fotos (album_id,archivo_foto,titulo,fecha,pais) SELECT ?,?,?,?,pais FROM usuarios WHERE id=?", Statement.RETURN_GENERATED_KEYS)) {
                                ps.setInt(1, albumId);
                                ps.setString(2, imagen.nombre());
                                ps.setString(3, imagen.nombre());
                                ps.setDate(4, java.sql.Date.valueOf(LocalDate.now()));
                                ps.setInt(5, (Integer) sesion.getAttribute("usuarioId"));
                                ps.executeUpdate();
                                try (ResultSet claves = ps.getGeneratedKeys()) {
                                    if (!claves.next()) throw new SQLException("No se pudo crear la foto.");
                                    fotoId = claves.getInt(1);
                                }
                            }
                        }
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO fotos_imagenes (foto_id,contenido,tipo) VALUES (?,?,?) ON DUPLICATE KEY UPDATE contenido=VALUES(contenido),tipo=VALUES(tipo)")) {
                            ps.setInt(1, fotoId);
                            ps.setBytes(2, imagen.contenido());
                            ps.setString(3, imagen.tipo());
                            ps.executeUpdate();
                        }
                        if (cambiar) {
                            try (PreparedStatement ps = c.prepareStatement("UPDATE fotos SET archivo_foto=? WHERE id=?")) {
                                ps.setString(1, imagen.nombre());
                                ps.setInt(2, fotoId);
                                ps.executeUpdate();
                            }
                        }
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM fotos WHERE id=?")) {
                        ps.setInt(1, fotoId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return albumId;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void servirImagen(Connection c, int fotoId, HttpServletResponse resp) throws SQLException, IOException {
        try (PreparedStatement ps = c.prepareStatement("SELECT i.contenido,i.tipo,f.archivo_foto FROM fotos f LEFT JOIN fotos_imagenes i ON i.foto_id=f.id WHERE f.id=? AND f.inapropiado=0")) {
            ps.setInt(1, fotoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.sendError(404);
                    return;
                }
                byte[] bytes = rs.getBytes("contenido");
                String tipo = rs.getString("tipo");
                if (bytes == null) {
                    // Compatibilidad con fotos anteriores guardadas como archivos.
                    String raiz = getServletContext().getRealPath("/uploads");
                    if (raiz != null) {
                        var directorio = Paths.get(raiz).toAbsolutePath().normalize();
                        var archivo = directorio.resolve(rs.getString("archivo_foto")).normalize();
                        if (archivo.startsWith(directorio) && Files.isRegularFile(archivo)) {
                            bytes = Files.readAllBytes(archivo);
                            tipo = getServletContext().getMimeType(archivo.toString());
                        }
                    }
                }
                if (bytes == null || tipo == null || !List.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(tipo)) {
                    resp.sendError(404);
                    return;
                }
                resp.setCharacterEncoding(null);
                resp.setContentType(tipo);
                resp.setContentLength(bytes.length);
                resp.getOutputStream().write(bytes);
            }
        }
    }

    private int identificador(HttpServletRequest req, String nombre) {
        try {
            int id = Integer.parseInt(req.getParameter(nombre));
            if (id > 0) return id;
        } catch (NumberFormatException e) {
        }
        throw new IllegalArgumentException("Identificador inválido.");
    }

    private void preparar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        req.setAttribute("nonce", nonce);
        resp.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                + "'; style-src 'nonce-" + nonce + "' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; "
                + "img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    }

    private String token(HttpSession sesion) {
        String valor = (String) sesion.getAttribute("csrf");
        if (valor == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            sesion.setAttribute("csrf", valor);
        }
        return valor;
    }

    private String texto(HttpServletRequest req, String nombre, int max) {
        String valor = req.getParameter(nombre);
        if (valor == null || valor.isBlank() || valor.trim().length() > max) {
            throw new IllegalArgumentException("El campo " + nombre + " es obligatorio y admite hasta " + max + " caracteres.");
        }
        return valor.trim();
    }

    private String nombreCorto(Object nombre) {
        if (nombre == null) return "";
        String[] palabras = nombre.toString().trim().split("\\s+");
        return palabras[0];
    }

    private String html(Object valor) {
        if (valor == null) return "";
        return valor.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String multilinea(String valor) {
        return html(valor).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }
}
