import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.*;

class NativeBootstrap {
 public static void main(String[] args)throws Exception{
  String base="jdbc:postgresql://127.0.0.1:55432/";
  Properties p=new Properties();p.setProperty("user","radar_admin");p.setProperty("password",System.getenv("RADAR_ADMIN_PASSWORD"));
  try(var c=DriverManager.getConnection(base+"postgres",p)){var r=c.createStatement().executeQuery("select 1 from pg_database where datname='radar_local'");if(!r.next())c.createStatement().execute("create database radar_local");}
  try(var c=DriverManager.getConnection(base+"radar_local",p)){
   c.createStatement().execute("create table if not exists radar_native_schema(version text primary key,checksum text not null,applied_at timestamptz not null default now())");
   Path project=Path.of(args[0]);
   try(var files=Files.list(project.resolve("backend/src/main/resources/db/migration"))){for(Path f:files.sorted().toList()){
    if(!f.getFileName().toString().startsWith("V"))continue;
    String text=Files.readString(f),hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
    var q=c.prepareStatement("select checksum from radar_native_schema where version=?");q.setString(1,f.getFileName().toString());var r=q.executeQuery();
    if(r.next()){if(!hash.equalsIgnoreCase(r.getString(1)))throw new IllegalStateException("Migration alterada: "+f);continue;}
    text=text.replace("CREATE EXTENSION IF NOT EXISTS \"vector\";","-- Native local: pgvector unused; no embedding support enabled.");
    c.setAutoCommit(false);try{c.createStatement().execute(text);var i=c.prepareStatement("insert into radar_native_schema(version,checksum) values(?,?)");i.setString(1,f.getFileName().toString());i.setString(2,hash);i.executeUpdate();c.commit();}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
    System.out.println("Aplicada "+f.getFileName());
   }}
   String app=System.getenv("RADAR_APP_PASSWORD");if(!app.matches("[A-F0-9]+"))throw new IllegalStateException("Invalid secret format");
   c.createStatement().execute("ALTER ROLE app_aplicacao WITH PASSWORD '"+app+"'");
   var r=c.createStatement().executeQuery("select count(*) from tenant");r.next();if(r.getInt(1)==0){String seed=Files.readString(project.resolve("infra/dados-demo.sql")).replaceAll("(?m)^\\\\.*$","");c.createStatement().execute(seed);System.out.println("Tenant de demonstração criado.");}
  }
  System.out.println("PostgreSQL nativo pronto. Sem Docker.");
 }
}
