/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package art;

import java.lang.invoke.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

public class Test1981 {
  public static void run() throws Exception {
    Redefinition.setTestConfiguration(Redefinition.Config.COMMON_REDEFINE);
    doTest();
  }

  private static final boolean PRINT_NONDETERMINISTIC = false;

  public static WeakHashMap<Object, Long> id_nums = new WeakHashMap<>();
  public static long next_id = 0;

  public static String printGeneric(Object o) {
    Long id = id_nums.get(o);
    if (id == null) {
      id = Long.valueOf(next_id++);
      id_nums.put(o, id);
    }
    if (o == null) {
      return "(ID: " + id + ") <NULL>";
    }
    Class oc = o.getClass();
    if (oc.isArray() && oc.getComponentType() == Byte.TYPE) {
      return "(ID: "
          + id
          + ") "
          + Arrays.toString(Arrays.copyOf((byte[]) o, 10)).replace(']', ',')
          + " ...]";
    } else if (o instanceof VarHandle) {
      // These don't have a good to-string. Give them one.
      VarHandle v = (VarHandle)o;
      return "(ID: " + id + ") " + o.getClass().getName() + "()->" + v.varType().getName();
    } else {
      return "(ID: " + id + ") " + o.toString();
    }
  }

  public static class Transform {
    static {
    }

    private static Object BAR =
        new Object() {
          public String toString() {
            return "value of <" + this.get() + ">";
          }

          public Object get() {
            return "BAR FIELD";
          }
        };
    private static Object FOO =
        new Object() {
          public String toString() {
            return "value of <" + this.get() + ">";
          }

          public Object get() {
            return "FOO FIELD";
          }
        };

    public static MethodHandles.Lookup getLookup() {
      return MethodHandles.lookup();
    }

    public static String staticToString() {
      return Transform.class.toString() + "[FOO: " + FOO + ", BAR: " + BAR + "]";
    }
  }

  /* Base64 encoded class of:
   * public static class Transform {
   *   static {}
   *   // NB This is the order the fields will be laid out in memory.
   *   private static Object BAR;
   *   private static Object BAZ;
   *   private static Object FOO;
   *   public static MethodHandles.Lookup getLookup() { return null; }
   *   private static void reinitialize() {
   *     BAZ = 42;
   *   }
   *   public static String staticToString() {
   *    return Transform.class.toString() + "[FOO: " + FOO + ", BAR: " + BAR + ", BAZ: " + BAZ + "]";
   *   }
   * }
   */
  private static byte[] REDEFINED_DEX_BYTES =
      Base64.getDecoder()
          .decode(
              "ZGV4CjAzNQDY+Vd3k8SVBE6A35RavIBzYN76h51YIqVwBgAAcAAAAHhWNBIAAAAAAAAAAKwFAAAk"
                  + "AAAAcAAAAAwAAAAAAQAABgAAADABAAADAAAAeAEAAAwAAACQAQAAAQAAAPABAABgBAAAEAIAABoD"
                  + "AAAjAwAALAMAADYDAAA+AwAAQwMAAEgDAABNAwAAUAMAAFMDAABXAwAAWwMAAHUDAACFAwAAqQMA"
                  + "AMkDAADcAwAA8QMAAAUEAAAZBAAANAQAAF0EAABsBAAAdwQAAHoEAACCBAAAhQQAAJIEAACaBAAA"
                  + "pQQAAKsEAAC5BAAAyQQAANMEAADaBAAA4wQAAAcAAAALAAAADAAAAA0AAAAOAAAADwAAABAAAAAR"
                  + "AAAAEgAAABMAAAAUAAAAFwAAAAkAAAAGAAAABAMAAAgAAAAIAAAAAAAAAAoAAAAJAAAADAMAAAoA"
                  + "AAAJAAAAFAMAAAgAAAAKAAAAAAAAABcAAAALAAAAAAAAAAEABwAEAAAAAQAHAAUAAAABAAcABgAA"
                  + "AAEABQACAAAAAQAFAAMAAAABAAQAHAAAAAEABQAeAAAAAQABAB8AAAAFAAEAIAAAAAYAAAAiAAAA"
                  + "BwAFAAMAAAAJAAUAAwAAAAkAAgAbAAAACQADABsAAAAJAAEAIAAAAAEAAAABAAAABwAAAAAAAAAV"
                  + "AAAAnAUAAGoFAAAAAAAABQAAAAIAAAD/AgAANgAAABwAAQBuEAUAAAAMAGIBAgBiAgAAYgMBACIE"
                  + "CQBwEAgABABuIAoABAAaABgAbiAKAAQAbiAJABQAGgAAAG4gCgAEAG4gCQAkABoAAQBuIAoABABu"
                  + "IAkANAAaABkAbiAKAAQAbhALAAQADAARAAEAAAAAAAAA9gIAAAIAAAASABEAAAAAAAAAAADuAgAA"
                  + "AQAAAA4AAAABAAEAAQAAAPICAAAEAAAAcBAHAAAADgABAAAAAQAAAPoCAAAJAAAAEwAqAHEQBgAA"
                  + "AAwAaQABAA4ACgAOAAkADgAPAA4AEQAOhwAUAA4AAAEAAAAAAAAAAQAAAAcAAAABAAAACAAHLCBC"
                  + "QVI6IAAHLCBCQVo6IAAIPGNsaW5pdD4ABjxpbml0PgADQkFSAANCQVoAA0ZPTwABSQABTAACTEkA"
                  + "AkxMABhMYXJ0L1Rlc3QxOTgxJFRyYW5zZm9ybTsADkxhcnQvVGVzdDE5ODE7ACJMZGFsdmlrL2Fu"
                  + "bm90YXRpb24vRW5jbG9zaW5nQ2xhc3M7AB5MZGFsdmlrL2Fubm90YXRpb24vSW5uZXJDbGFzczsA"
                  + "EUxqYXZhL2xhbmcvQ2xhc3M7ABNMamF2YS9sYW5nL0ludGVnZXI7ABJMamF2YS9sYW5nL09iamVj"
                  + "dDsAEkxqYXZhL2xhbmcvU3RyaW5nOwAZTGphdmEvbGFuZy9TdHJpbmdCdWlsZGVyOwAnTGphdmEv"
                  + "bGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXA7AA1UZXN0MTk4MS5qYXZhAAlUcmFuc2Zv"
                  + "cm0AAVYABltGT086IAABXQALYWNjZXNzRmxhZ3MABmFwcGVuZAAJZ2V0TG9va3VwAARuYW1lAAxy"
                  + "ZWluaXRpYWxpemUADnN0YXRpY1RvU3RyaW5nAAh0b1N0cmluZwAFdmFsdWUAB3ZhbHVlT2YAdn5+"
                  + "RDh7ImNvbXBpbGF0aW9uLW1vZGUiOiJkZWJ1ZyIsIm1pbi1hcGkiOjEsInNoYS0xIjoiYTgzNTJm"
                  + "MjU0ODg1MzYyY2NkOGQ5MDlkMzUyOWM2MDA5NGRkODk2ZSIsInZlcnNpb24iOiIxLjYuMjAtZGV2"
                  + "In0AAgMBIRgCAgQCGgQJHRcWAwAFAAAKAQoBCgCIgASgBQGBgAS0BQEJjAUBCswFAQmQBAAAAAAC"
                  + "AAAAWwUAAGEFAACQBQAAAAAAAAAAAAAAAAAAEAAAAAAAAAABAAAAAAAAAAEAAAAkAAAAcAAAAAIA"
                  + "AAAMAAAAAAEAAAMAAAAGAAAAMAEAAAQAAAADAAAAeAEAAAUAAAAMAAAAkAEAAAYAAAABAAAA8AEA"
                  + "AAEgAAAFAAAAEAIAAAMgAAAFAAAA7gIAAAEQAAADAAAABAMAAAIgAAAkAAAAGgMAAAQgAAACAAAA"
                  + "WwUAAAAgAAABAAAAagUAAAMQAAACAAAAjAUAAAYgAAABAAAAnAUAAAAQAAABAAAArAUAAA==");

  public static void doTest() throws Exception {
    try {
      System.out.println("Initial: " + Transform.staticToString());
      MethodHandles.Lookup lookup = Transform.getLookup();
      String[] names = new String[] { "FOO", "BAR", };
      MethodHandle[] handles =
          new MethodHandle[] {
            lookup.findStaticGetter(Transform.class, "FOO", Object.class),
            lookup.findStaticGetter(Transform.class, "BAR", Object.class),
          };
      VarHandle foo_handle = lookup.findStaticVarHandle(Transform.class, "FOO", Object.class);
      VarHandle[] var_handles =
          new VarHandle[] {
            foo_handle,
            lookup.findStaticVarHandle(Transform.class, "BAR", Object.class),
          };

      for (int i = 0; i < names.length; i++) {
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(handles[i])
                + " = "
                + printGeneric(handles[i].invoke()));
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(var_handles[i])
                + " = "
                + printGeneric(var_handles[i].get()));
      }
      MethodHandle old_field_write = lookup.findStaticSetter(Transform.class, "FOO", Object.class);

      System.out.println("Redefining Transform class");
      Redefinition.doCommonStructuralClassRedefinition(Transform.class, REDEFINED_DEX_BYTES);
      System.out.println("Post redefinition : " + Transform.staticToString());

      String[] new_names = new String[] { "BAZ", "FOO", "BAR", };
      MethodHandle[] new_handles =
          new MethodHandle[] {
            lookup.findStaticGetter(Transform.class, "BAZ", Object.class),
            lookup.findStaticGetter(Transform.class, "FOO", Object.class),
            lookup.findStaticGetter(Transform.class, "BAR", Object.class),
          };
      VarHandle baz_handle = lookup.findStaticVarHandle(Transform.class, "BAZ", Object.class);
      VarHandle[] new_var_handles =
          new VarHandle[] {
            baz_handle,
            lookup.findStaticVarHandle(Transform.class, "FOO", Object.class),
            lookup.findStaticVarHandle(Transform.class, "BAR", Object.class),
          };

      for (int i = 0; i < names.length; i++) {
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(handles[i])
                + " = "
                + printGeneric(handles[i].invoke()));
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(var_handles[i])
                + " = "
                + printGeneric(var_handles[i].get()));
      }

      for (int i = 0; i < new_names.length; i++) {
        System.out.println(
            "Reading new field "
                + new_names[i]
                + " using "
                + printGeneric(new_handles[i])
                + " = "
                + printGeneric(new_handles[i].invoke()));
        System.out.println(
            "Reading new field "
                + new_names[i]
                + " using "
                + printGeneric(new_var_handles[i])
                + " = "
                + printGeneric(new_var_handles[i].get()));
      }

      String val = "foo";
      System.out.println("Setting BAZ to " + printGeneric(val) + " with new mh.");
      lookup.findStaticSetter(Transform.class, "BAZ", Object.class).invoke(val);
      System.out.println("Post set with new mh: " + Transform.staticToString());

      System.out.println("Setting FOO to " + printGeneric(Transform.class) + " with old mh.");
      old_field_write.invoke(Transform.class);
      System.out.println("Post set with old mh: " + Transform.staticToString());

      Object new_val = new Object() {
        public String toString() {
          return "new_value object";
        }
      };
      System.out.println("Setting FOO to '" + printGeneric(new_val) + "' with old varhandle.");
      foo_handle.set(new_val);
      System.out.println("Post set with new varhandle: " + Transform.staticToString());

      System.out.println("Setting BAZ to 'bar' with new varhandle.");
      baz_handle.set("bar");
      System.out.println("Post set with old varhandle: " + Transform.staticToString());

      System.out.println("Using mh to call new private method.");
      MethodHandle reinit =
          lookup.findStatic(Transform.class, "reinitialize", MethodType.methodType(Void.TYPE));
      reinit.invoke();
      System.out.println("Post reinit with mh: " + Transform.staticToString());


      for (int i = 0; i < names.length; i++) {
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(handles[i])
                + " = "
                + printGeneric(handles[i].invoke()));
        System.out.println(
            "Reading field "
                + names[i]
                + " using "
                + printGeneric(var_handles[i])
                + " = "
                + printGeneric(var_handles[i].get()));
      }
      for (int i = 0; i < new_names.length; i++) {
        System.out.println(
            "Reading new field "
                + new_names[i]
                + " using "
                + printGeneric(new_handles[i])
                + " = "
                + printGeneric(new_handles[i].invoke()));
        System.out.println(
            "Reading new field "
                + new_names[i]
                + " using "
                + printGeneric(new_var_handles[i])
                + " = "
                + printGeneric(new_var_handles[i].get()));
      }
    } catch (Throwable t) {
      if (t instanceof Exception) {
        throw (Exception) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      } else {
        throw new RuntimeException("Unexpected throwable!", t);
      }
    }
  }
}
