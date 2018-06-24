package pe.mrodas.rest;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @see <a href="https://dennis-xlc.gitbooks.io/restful-java-with-jax-rs-2-0-en/cn/part1/chapter7/exception_handling.html">
 * dennis-xlc.gitbooks.io<br>Restful Exception Handling
 * </a>
 */
public abstract class JsonExceptionMapper implements ExceptionMapper<Exception> {

    private BiConsumer<Exception, ApiError> configApiError;
    private HashMap<String, Object> map;

    /**
     * Extracts an error message from the exception
     *
     * @param exception the captured exception
     * @return error message
     */
    protected String getMessage(Exception exception) {
        if (exception instanceof NotAuthorizedException) {
            List<Object> list = ((NotAuthorizedException) exception).getChallenges();
            return list.stream().map(Object::toString).collect(Collectors.joining("\n"));
        }
        return exception.getMessage();
    }

    /**
     * Extracts the HTTP Status from the exception, default 500: INTERNAL_SERVER_ERROR
     *
     * @param exception the captured exception
     * @return HTTP Status
     */
    protected Response.Status getStatus(Exception exception) {
        if (exception instanceof WebApplicationException) {
            int code = ((WebApplicationException) exception).getResponse().getStatus();
            return Response.Status.fromStatusCode(code);
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private String getTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String stackTrace = sw.toString();
        pw.close();
        return stackTrace;
    }

    private String getJson(ApiError error) {
        map = new HashMap<>();
        this.putIfNotNull("type", error.getType());
        this.putIfNotNull("message", error.getMessage());
        map.put("code", error.getCode());
        map.put("subCode", error.getSubCode());
        this.putIfNotNull("trace", error.getTrace());
        String json = map.entrySet().stream()
                .map(this::toJsonField)
                .collect(Collectors.joining(","));
        return String.format("{ %s }", json);
    }

    /**
     * Overwrites the error object default configuration.
     * Executed before build the Response
     *
     * @param configApiError BiConsumer configures the error object
     */
    public void setConfigApiError(BiConsumer<Exception, ApiError> configApiError) {
        this.configApiError = configApiError;
    }

    /**
     * Build a Response
     *
     * @param exception the captured exception
     * @param status    the HTTP status in the response
     * @param message   the error message
     * @param debugMode indicates if the error trace should be shown
     * @return an error response with a Json body
     */
    public Response getResponse(Exception exception, Response.Status status, String message, boolean debugMode) {
        ApiError error = new ApiError()
                .setType(status.getReasonPhrase())
                .setCode(status.getStatusCode())
                .setMessage(message);
        if (debugMode) {
            error.setTrace(this.getTrace(exception));
        }
        if (configApiError != null) {
            configApiError.accept(exception, error);
        }
        return Response.status(status)
                .entity(this.getJson(error))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private void putIfNotNull(String key, Object obj) {
        if (obj != null) {
            map.put(key, obj);
        }
    }

    private String toJsonField(Map.Entry<String, Object> entry) {
        String format = entry.getValue().getClass() == String.class
                ? "\"%s\" : \"%s\""
                : "\"%s\" : %s";
        return String.format(format, entry.getKey(), entry.getValue());
    }
}
