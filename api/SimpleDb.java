package api;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class SimpleDb {

    private static final int MAGIC = 1;
    private static final String deleted = "Tombstone";
    private final FileChannel fileChannel;
    private final Map<String, Long> index = new HashMap<>();


    public SimpleDb(String fileName) throws IOException {
        Path path = Path.of("/Users/aakashbasnet/Documents/mydb/data.db");
        Files.createDirectories(path.getParent());

        this.fileChannel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
                );
        System.out.println(path);
        recoverIndex();

    }

    public synchronized void post(String key, String value) throws IOException {

        System.out.println("File Channel size: "+fileChannel.size()+" while entering key: "+key+" and value: "+value);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        int recordSize = Integer.BYTES*2+ keyBytes.length+ valueBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(recordSize);

        //recording format
        buffer.putInt(keyBytes.length);
        buffer.putInt(valueBytes.length);
        buffer.put(keyBytes);
        buffer.put(valueBytes);
//        buffer.putInt(MAGIC);

        buffer.flip();

        long offset = fileChannel.size();
        fileChannel.position(offset);
        int totalWritten = 0;
        while(buffer.hasRemaining()){
            int written = fileChannel.write(buffer);
            totalWritten += written;
//            System.out.println("Written bytes this iteration: " + written);
        }

        index.put(key, offset);
        fileChannel.force(true);

    }

    public synchronized String get(String key) throws IOException {

        Long offset = index.get(key);
        System.out.println(index.toString());

        // Key not found in index
        if (offset == null) {
            return null;
        }

        // Move file cursor to the exact record location
        fileChannel.position(offset);

        // Step 1: read keyLength and valueLength (8 bytes total)
        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES * 2);
        while (header.hasRemaining()) {
            int bytesRead = fileChannel.read(header);
            if (bytesRead == -1) {
                throw new IOException("Unexpected end of file while reading header");
            }
        }
        header.flip();

        int keyLength = header.getInt();
        int valueLength = header.getInt();

        // Step 2: read key + value bytes
        ByteBuffer data = ByteBuffer.allocate(keyLength + valueLength);
        fileChannel.read(data);
        data.flip();

        byte[] keyBytes = new byte[keyLength];
        data.get(keyBytes);

        byte[] valueBytes = new byte[valueLength];
        data.get(valueBytes);

        String storedKey = new String(keyBytes, StandardCharsets.UTF_8);


        // Safety check (important for robustness)
        if (!storedKey.equals(key)) {
            throw new IllegalStateException("Key mismatch at offset " + offset);
        }

        String storedValue = new String(valueBytes, StandardCharsets.UTF_8);
        if(storedValue.equals(deleted)){
            return null;
        }

        return new String(valueBytes, StandardCharsets.UTF_8);
    }

    public synchronized String update(String key, String value) throws IOException {
        if(!index.containsKey(key)){
            return null;

        }
        Long offset = index.get(key);
        if(offset==null){
            return null;
        }

        fileChannel.position(offset);

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        int recordSize = Integer.BYTES*2+ keyBytes.length+ valueBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(recordSize);

        //recording format
        buffer.putInt(keyBytes.length);
        buffer.putInt(valueBytes.length);
        buffer.put(keyBytes);
        buffer.put(valueBytes);
//        buffer.putInt(MAGIC);

        buffer.flip();

        fileChannel.position(offset);
        int totalWritten = 0;
        while(buffer.hasRemaining()){
            int written = fileChannel.write(buffer);
            totalWritten += written;
//            System.out.println("Written bytes this iteration: " + written);
        }

        index.put(key, offset);
        fileChannel.force(true);
        return get(key);

    }

    public synchronized String patch(String key, String value) throws IOException {
        if(!index.containsKey(key)){
            return null;

        }
        Long offset = index.get(key);
        if(offset==null){
            return null;
        }
        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES*2);
        while(header.hasRemaining()){
            int written = fileChannel.write(header);

//            System.out.println("Written bytes this iteration: " + written);
        }
        header.flip();
        int keyLength = header.getInt();
        int valLength = header.getInt();


        int intermediaryBytes = Integer.BYTES*2+keyLength;
        long newPosition = offset+intermediaryBytes;
        fileChannel.position(newPosition);
        ByteBuffer zeroBuffer = ByteBuffer.allocate(valLength);
        zeroBuffer.flip();

        ByteBuffer buffer = ByteBuffer.allocate(valLength);
        buffer.put(value.getBytes(StandardCharsets.UTF_8));
        buffer.flip();


        while(zeroBuffer.hasRemaining()){
            fileChannel.write(zeroBuffer);
        }
        fileChannel.force(true);

        fileChannel.position(newPosition);
        int totalWritten = 0;
        while(buffer.hasRemaining()){
            int written = fileChannel.write(buffer);
            totalWritten += written;
//            System.out.println("Written bytes this iteration: " + written);
        }
        index.put(key, offset);
        fileChannel.force(true);
        return get(key);

    }

    public void delete(String key) throws IOException {
        if(!index.containsKey(key)){
            return;
        }
        update(key, deleted);
    }

    private void recoverIndex() throws IOException {
        long offset = 0;
        long fileSize = fileChannel.size();

        while (offset < fileSize) {
            fileChannel.position(offset);

            // ---- Read header ----
            ByteBuffer header = ByteBuffer.allocate(Integer.BYTES * 2);
            if (!readFully(header)) break;
            header.flip();

            int keyLength = header.getInt();
            int valueLength = header.getInt();


            // ---- Sanity checks ----
            if (keyLength <= 0 || valueLength < 0) {
                break; // corrupted or invalid record
            }

            // ---- Read key + value ----
            ByteBuffer data = ByteBuffer.allocate(keyLength + valueLength+Integer.BYTES);
            if (!readFully(data)) break;
            data.flip();

            byte[] keyBytes = new byte[keyLength];
            data.get(keyBytes);

            byte[] valueBytes = new byte[valueLength];
            data.get(valueBytes);

//            int magicData = data.getInt();



            String key = new String(keyBytes, StandardCharsets.UTF_8);
            String value = new String(valueBytes, StandardCharsets.UTF_8);
            if(value.equals(deleted)){
                return;
            }

            // Update index to latest occurrence
            //rebuilding index
            index.put(key, offset);

            // Move to next record of key and value pair
            offset += Integer.BYTES * 2 + keyLength + valueLength;
        }
    }

    private boolean readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int bytesRead = fileChannel.read(buffer);
            if (bytesRead == -1) {
                return false; // EOF
            }
        }
        return true;
    }


}
