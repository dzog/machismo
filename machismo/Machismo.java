package dzog.machismo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Machismo 0.1
 * by dzog
 *
 * TODO: refactor/cleanup; pretty print parsing of cpu type/subtype
 */
public class Machismo {
    private RandomAccessFile binary;

    private MachFile machFile;
    private FatFile fatFile;

    public Machismo(String pathToMachOBinary) throws IOException {
        binary = new RandomAccessFile(pathToMachOBinary,"r");

        binary.seek(0);
        byte[] magicBytes = new byte[4];
        binary.readFully(magicBytes);

        for(int i=0;i<2;i++) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(magicBytes);
            ByteOrder byteOrder = (i == 0) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            byteBuffer.order(byteOrder);
            switch(byteBuffer.getInt()) {
                case MagicNumbers.FAT :
                    fatFile = new FatFile(binary, byteOrder);
                    break;
                case MagicNumbers.OBJECT32 :
                    machFile = new MachFile(binary, 0, byteOrder, MachFile.Type.OBJ32);
                    break;
                case MagicNumbers.OBJECT64 :
                    machFile = new MachFile(binary, 0, byteOrder, MachFile.Type.OBJ64);
                    break;
            }
        }
    }

    public boolean isFatFile() {
        return fatFile != null;
    }
    public boolean isMachFile() {
        return machFile != null;
    }
    public FatFile getFatFile() {
        return fatFile;
    }
    public MachFile getMachFile() {
        return machFile;
    }

    /* Data Definitions */

    static class FatFile {
        private RandomAccessFile binary;
        private ByteOrder byteOrder;
        private FatHeader header;
        private List<MachFile> machFiles;

        FatFile(RandomAccessFile binary, ByteOrder byteOrder) throws IOException {
            this.binary = binary;
            this.byteOrder = byteOrder;
            header = new FatHeader();
        }

        public ByteOrder getByteOrder() {
            return byteOrder;
        }
        public FatHeader getFatHeader() {
            return header;
        }
        public List<MachFile> getMachFiles() {
            return machFiles;
        }

        class FatHeader {
            int magic;
            int numFatArch;

            List<FatArchHeader> fatArchHeaders;

            FatHeader() throws IOException {
                populate();
            }

            private void populate() throws IOException {
                binary.seek(0);
                magic = readNextFourBytes(binary, byteOrder);
                numFatArch = readNextFourBytes(binary, byteOrder);

                fatArchHeaders = new ArrayList<FatArchHeader>(numFatArch);

                machFiles = new ArrayList<MachFile>(numFatArch);

                for(int i=0; i<numFatArch; i++) {
                    FatArchHeader fatArchHeader = new FatArchHeader();
                    fatArchHeader.readAndPopulate();
                    fatArchHeaders.add(fatArchHeader);
                }
                for(FatArchHeader fatArchHeader : fatArchHeaders) {
                    machFiles.add(new MachFile(binary, fatArchHeader));
                }
            }
        }
        class FatArchHeader {
            int cpuType;
            int cpuSubtype;
            int offset;
            int size;
            int align;

            private void readAndPopulate() throws IOException {
                cpuType = readNextFourBytes(binary, byteOrder);
                cpuSubtype = readNextFourBytes(binary, byteOrder);
                offset = readNextFourBytes(binary, byteOrder);
                size = readNextFourBytes(binary, byteOrder);
                align = readNextFourBytes(binary, byteOrder);
            }
        }
    }

    static class MachFile {
        enum Type {
            OBJ32,
            OBJ64;
        }
        private RandomAccessFile binary;
        private Type type;
        private ByteOrder byteOrder;
        private MachHeader machHeader;
        private FatFile.FatArchHeader fatArchHeader;
        private int pos;

        private LoadCommand uuid = null;

        MachFile(RandomAccessFile binary, int pos, ByteOrder byteOrder, Type type) throws IOException {
            this.binary = binary;
            this.pos = pos;
            binary.seek(pos);
            if(byteOrder == null || type == null) {
                this.byteOrder = determineByteOrder(pos); //sets type side-effectly TODO fix
            } else {
                this.byteOrder = byteOrder;
                this.type = type;
            }
            machHeader = new MachHeader();
            machHeader.readAndPopulate();
        }

        MachFile(RandomAccessFile binary, int pos) throws IOException {
            this(binary, pos, null, null);
        }

        MachFile(RandomAccessFile binary, FatFile.FatArchHeader fatArchHeader) throws IOException {
            this.binary = binary;
            this.fatArchHeader = fatArchHeader;
            binary.seek(fatArchHeader.offset);
            this.byteOrder = determineByteOrder(fatArchHeader.offset);
            binary.seek(fatArchHeader.offset);

            machHeader = new MachHeader();
            machHeader.readAndPopulate();
        }

        public Type getType() {
            return type;
        }
        public ByteOrder getByteOrder() {
            return byteOrder;
        }
        public MachHeader getMachHeader() {
            return machHeader;
        }
        public int getPosition() {
            return pos;
        }
        public String getUUIDString() {
            return this.machHeader.uuidString;
        }

        //todo make side effecty only
         private ByteOrder determineByteOrder(int pos) throws IOException {
            binary.seek(pos);
            byte[] magicBytes = new byte[4];
            binary.readFully(magicBytes);
            for(int i=0;i<2;i++) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(magicBytes);
                ByteOrder _byteOrder = (i == 0) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                byteBuffer.order(_byteOrder);
                switch(byteBuffer.getInt()) {
                    case MagicNumbers.OBJECT32 :
                        type = Type.OBJ32;
                        return _byteOrder;
                    case MagicNumbers.OBJECT64 :
                        type = Type.OBJ64;
                        return _byteOrder;
                }
            }
            throw new IOException();
        }

        public class MachHeader {
            int magic;
            int cpuType;
            int cpuSubtype;
            int fileType;
            int numLoadCommands;
            int sizeOfLoadCommands;
            int flags;
            int ext64Bit;

            private String uuidString;

            private void readAndPopulate() throws IOException {
                magic = readNextFourBytes(binary, byteOrder);
                cpuType = readNextFourBytes(binary, byteOrder);
                cpuSubtype = readNextFourBytes(binary, byteOrder);
                fileType = readNextFourBytes(binary, byteOrder);
                numLoadCommands = readNextFourBytes(binary, byteOrder);
                sizeOfLoadCommands = readNextFourBytes(binary, byteOrder);
                flags = readNextFourBytes(binary, byteOrder);

                if(type == Type.OBJ64) {
                    ext64Bit = readNextFourBytes(binary, byteOrder);
                } else {
                    ext64Bit = 0;
                }

                for(int i=0;i<numLoadCommands;i++) {
                    LoadCommand lc = new LoadCommand();
                    lc.readAndPopulate();

                    /* UUID */
                    if(lc.cmd == 0x1b) {
                        uuid = lc;
                        //System.out.print("uuid found: ");
                        byte[] uuid = new byte[lc.cmdSize - 8];
                        binary.readFully(uuid);
                        ByteBuffer byteBuffer = ByteBuffer.wrap(uuid);
                        byteBuffer.order(ByteOrder.BIG_ENDIAN); //uuid is always big-endian (per rfc4122)
                        BigInteger bi = new BigInteger(1, byteBuffer.array());
                        String uuidString = String.format("%0" + (uuid.length << 1) + "X", bi);
                        //System.out.println(uuidString);
                        this.uuidString = uuidString;
                    } else {
                        binary.skipBytes(lc.cmdSize - 8);
                    }
                }
            }
        }

        class LoadCommand {
            int cmd;
            int cmdSize;

            public void readAndPopulate() throws IOException {
                cmd = readNextFourBytes(binary, byteOrder);
                cmdSize = readNextFourBytes(binary, byteOrder);
            }
        }
    }

    /* Util Functions */

    public static int readNextFourBytes(RandomAccessFile binary, ByteOrder byteOrder) throws IOException {
        byte[] magicBytes = new byte[4];
        binary.readFully(magicBytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(magicBytes);
        byteBuffer.order(byteOrder);
        return byteBuffer.getInt();
    }

    static class MagicNumbers {
        public static final int OBJECT32 = 0xFEEDFACE;
        public static final int OBJECT64 = 0xFEEDFACF;
        public static final int FAT = 0xCAFEBABE;

        public static final int LC_UUID = 0x0000001B;
    }

}
