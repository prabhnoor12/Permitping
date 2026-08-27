package com.permitping.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

public final class FileStorage {
    private final Path root;
    public FileStorage(Path root) { this.root=root; }
    public Path copyIntoStorage(long documentId, Path source) throws IOException { if(documentId<=0||source==null||!Files.isRegularFile(source))throw new IOException("Selected file does not exist"); Path destination=root.resolve(Long.toString(documentId)).resolve(source.getFileName());Files.createDirectories(destination.getParent());return Files.copy(source,destination,StandardCopyOption.REPLACE_EXISTING); }
    public Path importFile(Path source) throws IOException { if(source==null||!Files.isRegularFile(source)) throw new IOException("Selected file does not exist"); Path destination=root.resolve(java.util.UUID.randomUUID().toString()).resolve(source.getFileName().toString());Files.createDirectories(destination.getParent());return Files.copy(source,destination); }
    public boolean isManaged(String path) { if(path==null||path.isBlank())return false;try{return Path.of(path).toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());}catch(Exception e){return false;} }
    public boolean isDuplicate(Path source, String excludingPath) throws IOException { if(source==null||!Files.isRegularFile(source))return false;String hash=sha256(source);if(!Files.exists(root))return false;try(var stream=Files.walk(root)){return stream.filter(Files::isRegularFile).filter(p->excludingPath==null||!p.toAbsolutePath().normalize().toString().equalsIgnoreCase(Path.of(excludingPath).toAbsolutePath().normalize().toString())).anyMatch(p->{try{return sha256(p).equals(hash);}catch(IOException e){return false;}});}}
    private String sha256(Path path) throws IOException {try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
    public boolean exists(String path) { try { return path!=null&&!path.isBlank()&&Files.isRegularFile(Path.of(path)); } catch (RuntimeException ex) { return false; } }
}
