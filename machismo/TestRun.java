package dzog.machismo;

import java.io.IOException;

/**
 * demonstration of Machismo : print some info about a Mach-O binary
 * (e.g. within in an unzipped IPA, ./Payload/SkillzTest.app/SkillzTest/)
 *
 * all file reading is done during construction of the Machismo object
 *
 * the constructor will throw an IOException if any IO or file validity errors are hit
 *
 *
 * sample output (single arch binary):
 *
 * binary file is single binary
 * Type: OBJ32
 * UUID: 1177A42E43233DD99EECA862AD37F810
 * cpuType: 12
 * cpuSubtype: 9
 *
 *
 * sample output (FAT binary):
 *
 * binary file is FAT
 * there are 3 mach-o binary objects
 * 
 * Mach-O Object # 0
 * ----------------------
 * Type: OBJ32
 * UUID: C18FE951BB693C2ABED6A9FB35C3FF04
 * cpuType: 12
 * cpuSubtype: 9
 *
 * Mach-O Object # 1
 * ----------------------
 * Type: OBJ32
 * UUID: 16F95E239D2730BA8174ADB517E38633
 * cpuType: 12
 * cpuSubtype: 11
 *
 * Mach-O Object # 2
 * ----------------------
 * Type: OBJ64
 * UUID: CA11BC928E903A1EB24DF56E5942A825
 * cpuType: 16777228
 * cpuSubtype: 0
 *
 *
 */
public class TestRun {
      public static void run(String filename) {
          Machismo machismo;
          try {
            machismo = new Machismo(filename);
          } catch (IOException e) {
              System.out.println("binary file is invalid or does not exist");
              return;
          }

          if(machismo.isFatFile()) {
              System.out.println("binary file is FAT");
              Machismo.FatFile fatFile = machismo.getFatFile();
              System.out.println("there are " + fatFile.getMachFiles().size() + " mach-o binary objects");
              System.out.println("");
              int i=0;
              for(Machismo.MachFile machFile : fatFile.getMachFiles()) {
                  System.out.println("Mach-O Object # " + i);
                  System.out.println("----------------------");
                  System.out.println("       Type: " + machFile.getType());
                  System.out.println("       UUID: " + machFile.getUUIDString());
                  System.out.println("    cpuType: " + machFile.getMachHeader().cpuType);
                  System.out.println(" cpuSubtype: " + machFile.getMachHeader().cpuSubtype);
                  System.out.println("");
                  i++;
              }
          }
          if(machismo.isMachFile()) {
              Machismo.MachFile machFile = machismo.getMachFile();
              System.out.println("binary file is single binary");
              System.out.println("       Type: " + machFile.getType());
              System.out.println("       UUID: " + machFile.getUUIDString());
              System.out.println("    cpuType: " + machFile.getMachHeader().cpuType);
              System.out.println(" cpuSubtype: " + machFile.getMachHeader().cpuSubtype);
          }
      }
}
