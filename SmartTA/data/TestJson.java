import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.*;
import com.fasterxml.jackson.datatype.jsr310.*;
import java.nio.file.*;
import java.util.*;

public class TestJson {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        String path = "D:/Tomcat/apache-tomcat-10.1.48/webapps/SmartTA/data/positions.json";
        String json = Files.readString(Path.of(path));
        
        System.out.println("JSON length: " + json.length());
        System.out.println("JSON preview: " + json.substring(0, Math.min(100, json.length())));
        
        List<?> list = mapper.readValue(json, new TypeReference<List<?>>(){});
        System.out.println("Parsed list size: " + list.size());
        System.out.println("First item: " + list.get(0));
    }
}
