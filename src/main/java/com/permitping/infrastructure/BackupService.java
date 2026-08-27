package com.permitping.infrastructure;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class BackupService {
    private final Path databasePath;
    public BackupService(Path databasePath) { this.databasePath = databasePath.toAbsolutePath().normalize(); }
    public Path createAutomaticBackup(Path directory, int keep) throws Exception {
        if (keep < 1) throw new IllegalArgumentException("Backup retention must be at least one file");
        Files.createDirectories(directory); Path destination=directory.resolve("permitping-backup-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".db"); String escaped=destination.toString().replace("'", "''");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+databasePath);Statement s=c.createStatement()){s.execute("VACUUM INTO '"+escaped+"'");} verify(destination); rotate(directory,keep); return destination;
    }
    public void verify(Path backup) throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+backup.toAbsolutePath());Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IllegalStateException("Backup integrity check failed");} }
    /** Restores after validating the backup. The application must be closed before calling this method. */
    public Path restoreBackup(Path backup) throws Exception {
        verify(backup); Path staged=databasePath.resolveSibling(databasePath.getFileName()+".restore-"+UUID.randomUUID()+".db"); Files.copy(backup,staged,StandardCopyOption.REPLACE_EXISTING); verify(staged);
        Path previous=databasePath.resolveSibling(databasePath.getFileName()+".before-restore-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".db");
        if(Files.exists(databasePath))Files.move(databasePath,previous,StandardCopyOption.REPLACE_EXISTING);Files.move(staged,databasePath,StandardCopyOption.REPLACE_EXISTING);return previous;
    }
    private void rotate(Path directory,int keep)throws Exception{try(var paths=Files.list(directory)){List<Path> backups=paths.filter(p->p.getFileName().toString().startsWith("permitping-backup-")&&p.getFileName().toString().endsWith(".db")).sorted(Comparator.reverseOrder()).toList();for(Path old:backups.subList(Math.min(keep,backups.size()),backups.size()))Files.deleteIfExists(old);}}
}
