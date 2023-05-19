// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

package com.alibaba.nacos.common.packagescan.classreading;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copy from ASM, with less modifications
 * A parser to make a  visit a ClassFile structure, as defined in the Java
 * Virtual Machine Specification (JVMS). This class parses the ClassFile content and calls the
 * appropriate visit methods of a given ClassVisitor for each field, method and bytecode
 * instruction encountered.
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html">JVMS 4</a>
 */
public class ClassReader {

    /**
     * A flag to skip the Code attributes. If this flag is set the Code attributes are neither parsed
     * nor visited.
     */
    public static final int SKIP_CODE = 1;

    public static final int V19 = 0 << 16 | 63;

    /**
     * A flag to skip the SourceFile.
     */
    public static final int SKIP_DEBUG = 2;

    /**
     * A flag to skip the StackMap and StackMapTable attributes. If this flag is set these attributes
     * are neither parsed nor visited  is not called). This flag
     * is useful when the option is used: it avoids visiting frames
     * that will be ignored and recomputed from scratch.
     */
    public static final int SKIP_FRAMES = 4;

    /**
     * A flag to expand the stack map frames. By default stack map frames are visited in their
     * original format (i.e. "expanded" for classes whose version is less than V1_6, and "compressed"
     * for the other classes). If this flag is set, stack map frames are always visited in expanded
     * format (this option adds a decompression/compression step in ClassReader and ClassWriter which
     * degrades performance quite a lot).
     */
    public static final int EXPAND_FRAMES = 8;

    /**
     * A flag to expand the ASM specific instructions into an equivalent sequence of standard bytecode
     * instructions. When resolving a forward jump it may happen that the signed 2 bytes offset
     * reserved for it is not sufficient to store the bytecode offset.
     * This internal flag is used to re-read classes containing such instructions,
     * in order to replace them with standard instructions. In addition, when this
     * flag is used, goto_w and jsr_w are <i>not</i> converted into goto and jsr, to make sure that
     * infinite loops where a goto_w is replaced with a goto in ClassReader and converted back to a
     * goto_w in ClassWriter cannot occur.
     */
    static final int EXPAND_ASM_INSNS = 256;

    /**
     * The maximum size of array to allocate.
     */
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;

    /**
     * The size of the temporary byte array used to read class input streams chunk by chunk.
     */
    private static final int INPUT_STREAM_DATA_CHUNK_SIZE = 4096;

    /**
     * A byte array containing the JVMS ClassFile structure to be parsed.
     *
     * @deprecated Use {@link #readByte(int)} and the other read methods instead. This field will
     * eventually be deleted.
     */
    @Deprecated
    // DontCheck(MemberName): can't be renamed (for backward binary compatibility).
    public final byte[] b;

    /**
     * The offset in bytes of the ClassFile's access_flags field.
     */
    public final int header;

    /**
     * A byte array containing the JVMS ClassFile structure to be parsed. <i>The content of this array
     * must not be modified. This field is intended for  Attribute sub classes, and is normally
     * not needed by class visitors.</i>
     *
     * <p>NOTE: the ClassFile structure can start at any offset within this array, i.e. it does not
     * necessarily start at offset 0. Use {@link #getItem} and {@link #header} to get correct
     * ClassFile element offsets within this byte array.
     */
    final byte[] classFileBuffer;

    /**
     * The offset in bytes, in {@link #classFileBuffer}, of each cp_info entry of the ClassFile's
     * constant_pool array, <i>plus one</i>. In other words, the offset of constant pool entry i is
     * given by cpInfoOffsets[i] - 1, i.e. its cp_info's tag field is given by b[cpInfoOffsets[i] -
     * 1].
     */
    private final int[] cpInfoOffsets;

    /**
     * The String objects corresponding to the CONSTANT_Utf8 constant pool items. This cache avoids
     * multiple parsing of a given CONSTANT_Utf8 constant pool item.
     */
    private final String[] constantUtf8Values;

    /**
     * A conservative estimate of the maximum length of the strings contained in the constant pool of
     * the class.
     */
    private final int maxStringLength;

    // -----------------------------------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------------------------------

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param classFile the JVMS ClassFile structure to be read.
     */
    public ClassReader(final byte[] classFile) {
        this(classFile, 0, classFile.length);
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param classFileBuffer a byte array containing the JVMS ClassFile structure to be read.
     * @param classFileOffset the offset in byteBuffer of the first byte of the ClassFile to be read.
     * @param classFileLength the length in bytes of the ClassFile to be read.
     */
    public ClassReader(
            final byte[] classFileBuffer,
            final int classFileOffset,
            final int classFileLength) { // NOPMD(UnusedFormalParameter) used for backward compatibility.
        this(classFileBuffer, classFileOffset, /* checkClassVersion = */ true);
    }

    /**
     * Constructs a new {@link ClassReader} object. <i>This internal constructor must not be exposed
     * as a public API</i>.
     *
     * @param classFileBuffer   a byte array containing the JVMS ClassFile structure to be read.
     * @param classFileOffset   the offset in byteBuffer of the first byte of the ClassFile to be read.
     * @param checkClassVersion whether to check the class version or not.
     */
    ClassReader(
            final byte[] classFileBuffer, final int classFileOffset, final boolean checkClassVersion) {
        this.classFileBuffer = classFileBuffer;
        this.b = classFileBuffer;
        // Check the class' major_version. This field is after the magic and minor_version fields, which
        // use 4 and 2 bytes respectively.
        if (checkClassVersion && readShort(classFileOffset + 6) > V19) {
            throw new IllegalArgumentException(
                    "Unsupported class file major version " + readShort(classFileOffset + 6));
        }
        // Create the constant pool arrays. The constant_pool_count field is after the magic,
        // minor_version and major_version fields, which use 4, 2 and 2 bytes respectively.
        int constantPoolCount = readUnsignedShort(classFileOffset + 8);
        cpInfoOffsets = new int[constantPoolCount];
        constantUtf8Values = new String[constantPoolCount];
        // Compute the offset of each constant pool entry, as well as a conservative estimate of the
        // maximum length of the constant pool strings. The first constant pool entry is after the
        // magic, minor_version, major_version and constant_pool_count fields, which use 4, 2, 2 and 2
        // bytes respectively.
        int currentCpInfoIndex = 1;
        int currentCpInfoOffset = classFileOffset + 10;
        int currentMaxStringLength = 0;
        boolean hasBootstrapMethods = false;
        boolean hasConstantDynamic = false;
        // The offset of the other entries depend on the total size of all the previous entries.
        while (currentCpInfoIndex < constantPoolCount) {
            cpInfoOffsets[currentCpInfoIndex++] = currentCpInfoOffset + 1;
            int cpInfoSize;
            switch (classFileBuffer[currentCpInfoOffset]) {
                case Symbol.CONSTANT_FIELDREF_TAG:
                case Symbol.CONSTANT_METHODREF_TAG:
                case Symbol.CONSTANT_INTERFACE_METHODREF_TAG:
                case Symbol.CONSTANT_INTEGER_TAG:
                case Symbol.CONSTANT_FLOAT_TAG:
                case Symbol.CONSTANT_NAME_AND_TYPE_TAG:
                    cpInfoSize = 5;
                    break;
                case Symbol.CONSTANT_DYNAMIC_TAG:
                    cpInfoSize = 5;
                    hasBootstrapMethods = true;
                    hasConstantDynamic = true;
                    break;
                case Symbol.CONSTANT_INVOKE_DYNAMIC_TAG:
                    cpInfoSize = 5;
                    hasBootstrapMethods = true;
                    break;
                case Symbol.CONSTANT_LONG_TAG:
                case Symbol.CONSTANT_DOUBLE_TAG:
                    cpInfoSize = 9;
                    currentCpInfoIndex++;
                    break;
                case Symbol.CONSTANT_UTF8_TAG:
                    cpInfoSize = 3 + readUnsignedShort(currentCpInfoOffset + 1);
                    if (cpInfoSize > currentMaxStringLength) {
                        // The size in bytes of this CONSTANT_Utf8 structure provides a conservative estimate
                        // of the length in characters of the corresponding string, and is much cheaper to
                        // compute than this exact length.
                        currentMaxStringLength = cpInfoSize;
                    }
                    break;
                case Symbol.CONSTANT_METHOD_HANDLE_TAG:
                    cpInfoSize = 4;
                    break;
                case Symbol.CONSTANT_CLASS_TAG:
                case Symbol.CONSTANT_STRING_TAG:
                case Symbol.CONSTANT_METHOD_TYPE_TAG:
                case Symbol.CONSTANT_PACKAGE_TAG:
                case Symbol.CONSTANT_MODULE_TAG:
                    cpInfoSize = 3;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            currentCpInfoOffset += cpInfoSize;
        }
        maxStringLength = currentMaxStringLength;
        // The Classfile's access_flags field is just after the last constant pool entry.
        header = currentCpInfoOffset;
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param inputStream an input stream of the JVMS ClassFile structure to be read. This input
     *                    stream must contain nothing more than the ClassFile structure itself. It is read from its
     *                    current position to its end.
     * @throws IOException if a problem occurs during reading.
     */
    public ClassReader(final InputStream inputStream) throws IOException {
        this(readStream(inputStream, false));
    }

    /**
     * Constructs a new {@link ClassReader} object.
     *
     * @param className the fully qualified name of the class to be read. The ClassFile structure is
     *                  retrieved with the current class loader's {@link ClassLoader#getSystemResourceAsStream}.
     * @throws IOException if an exception occurs during reading.
     */
    public ClassReader(final String className) throws IOException {
        this(
                readStream(
                        ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class"), true));
    }

    /**
     * Reads the given input stream and returns its content as a byte array.
     *
     * @param inputStream an input stream.
     * @param close       true to close the input stream after reading.
     * @return the content of the given input stream.
     * @throws IOException if a problem occurs during reading.
     */
    private static byte[] readStream(final InputStream inputStream, final boolean close)
            throws IOException {
        if (inputStream == null) {
            throw new IOException("Class not found");
        }
        int bufferSize = calculateBufferSize(inputStream);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] data = new byte[bufferSize];
            int bytesRead;
            int readCount = 0;
            while ((bytesRead = inputStream.read(data, 0, bufferSize)) != -1) {
                outputStream.write(data, 0, bytesRead);
                readCount++;
            }
            outputStream.flush();
            if (readCount == 1) {
                // SPRING PATCH: some misbehaving InputStreams return -1 but still write to buffer (gh-27429)
                // return data;
                // END OF PATCH
            }
            return outputStream.toByteArray();
        } finally {
            if (close) {
                inputStream.close();
            }
        }
    }

    private static int calculateBufferSize(final InputStream inputStream) throws IOException {
        int expectedLength = inputStream.available();
        /*
         * Some implementations can return 0 while holding available data
         * (e.g. new FileInputStream("/proc/a_file"))
         * Also in some pathological cases a very small number might be returned,
         * and in this case we use default size
         */
        if (expectedLength < 256) {
            return INPUT_STREAM_DATA_CHUNK_SIZE;
        }
        return Math.min(expectedLength, MAX_BUFFER_SIZE);
    }

    // -----------------------------------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------------------------------

    /**
     * Returns the class's access flags . This value may not reflect Deprecated
     * and Synthetic flags when bytecode is before 1.5 and those flags are represented by attributes.
     *
     * @return the class access flags.
     */
    public int getAccess() {
        return readUnsignedShort(header);
    }

    /**
     * the internal class name.
     */
    public String getClassName() {
        // this_class is just after the access_flags field (using 2 bytes).
        return readClass(header + 2, new char[maxStringLength]);
    }

    public String getSuperName() {
        // super_class is after the access_flags and this_class fields (2 bytes each).
        return readClass(header + 4, new char[maxStringLength]);
    }

    /**
     * the internal names of the directly implemented interfaces. Inherited implemented
     * interfaces are not returned.
     */
    public String[] getInterfaces() {
        // interfaces_count is after the access_flags, this_class and super_class fields (2 bytes each).
        int currentOffset = header + 6;
        int interfacesCount = readUnsignedShort(currentOffset);
        String[] interfaces = new String[interfacesCount];
        if (interfacesCount > 0) {
            char[] charBuffer = new char[maxStringLength];
            for (int i = 0; i < interfacesCount; ++i) {
                currentOffset += 2;
                interfaces[i] = readClass(currentOffset, charBuffer);
            }
        }
        return interfaces;
    }

    // ----------------------------------------------------------------------------------------------
    // Methods to parse attributes
    // ----------------------------------------------------------------------------------------------

    /**
     * Returns the offset in {@link #classFileBuffer} of the first ClassFile's 'attributes' array
     * field entry.
     *
     * @return the offset in {@link #classFileBuffer} of the first ClassFile's 'attributes' array
     * field entry.
     */
    final int getFirstAttributeOffset() {
        // Skip the access_flags, this_class, super_class, and interfaces_count fields (using 2 bytes
        // each), as well as the interfaces array field (2 bytes per interface).
        int currentOffset = header + 8 + readUnsignedShort(header + 6) * 2;

        // Read the fields_count field.
        int fieldsCount = readUnsignedShort(currentOffset);
        currentOffset += 2;
        // Skip the 'fields' array field.
        while (fieldsCount-- > 0) {
            // Invariant: currentOffset is the offset of a field_info structure.
            // Skip the access_flags, name_index and descriptor_index fields (2 bytes each), and read the
            // attributes_count field.
            int attributesCount = readUnsignedShort(currentOffset + 6);
            currentOffset += 8;
            // Skip the 'attributes' array field.
            while (attributesCount-- > 0) {
                // Invariant: currentOffset is the offset of an attribute_info structure.
                // Read the attribute_length field (2 bytes after the start of the attribute_info) and skip
                // this many bytes, plus 6 for the attribute_name_index and attribute_length fields
                // (yielding the total size of the attribute_info structure).
                currentOffset += 6 + readInt(currentOffset + 2);
            }
        }

        // Skip the methods_count and 'methods' fields, using the same method as above.
        int methodsCount = readUnsignedShort(currentOffset);
        currentOffset += 2;
        while (methodsCount-- > 0) {
            int attributesCount = readUnsignedShort(currentOffset + 6);
            currentOffset += 8;
            while (attributesCount-- > 0) {
                currentOffset += 6 + readInt(currentOffset + 2);
            }
        }

        // Skip the ClassFile's attributes_count field.
        return currentOffset + 2;
    }

    // -----------------------------------------------------------------------------------------------
    // Utility methods: low level parsing
    // -----------------------------------------------------------------------------------------------

    /**
     * Returns the number of entries in the class's constant pool table.
     *
     * @return the number of entries in the class's constant pool table.
     */
    public int getItemCount() {
        return cpInfoOffsets.length;
    }

    /**
     * Returns the start offset in this {@link ClassReader} of a JVMS 'cp_info' structure (i.e. a
     * constant pool entry), plus one. <i>This method is intended for  Attribute sub classes,
     * and is normally not needed by class generators or adapters.</i>
     *
     * @param constantPoolEntryIndex the index a constant pool entry in the class's constant pool
     *                               table.
     * @return the start offset in this {@link ClassReader} of the corresponding JVMS 'cp_info'
     * structure, plus one.
     */
    public int getItem(final int constantPoolEntryIndex) {
        return cpInfoOffsets[constantPoolEntryIndex];
    }

    /**
     * Returns a conservative estimate of the maximum length of the strings contained in the class's
     * constant pool table.
     *
     * @return a conservative estimate of the maximum length of the strings contained in the class's
     * constant pool table.
     */
    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * Reads a byte value in this {@link ClassReader}. <i>This method is intended for
     * Attribute sub classes, and is normally not needed by class generators or adapters.</i>
     *
     * @param offset the start offset of the value to be read in this {@link ClassReader}.
     * @return the read value.
     */
    public int readByte(final int offset) {
        return classFileBuffer[offset] & 0xFF;
    }

    /**
     * Reads an unsigned short value in this {@link ClassReader}. <i>This method is intended for
     *  Attribute sub classes, and is normally not needed by class generators or adapters.</i>
     *
     * @param offset the start index of the value to be read in this {@link ClassReader}.
     * @return the read value.
     */
    public int readUnsignedShort(final int offset) {
        byte[] classBuffer = classFileBuffer;
        return ((classBuffer[offset] & 0xFF) << 8) | (classBuffer[offset + 1] & 0xFF);
    }

    /**
     * Reads a signed short value in this {@link ClassReader}. <i>This method is intended for
     * Attribute sub classes, and is normally not needed by class generators or adapters.</i>
     *
     * @param offset the start offset of the value to be read in this {@link ClassReader}.
     * @return the read value.
     */
    public short readShort(final int offset) {
        byte[] classBuffer = classFileBuffer;
        return (short) (((classBuffer[offset] & 0xFF) << 8) | (classBuffer[offset + 1] & 0xFF));
    }

    /**
     * Reads a signed int value in this {@link ClassReader}. <i>This method is intended for
     * Attribute sub classes, and is normally not needed by class generators or adapters.</i>
     *
     * @param offset the start offset of the value to be read in this {@link ClassReader}.
     * @return the read value.
     */
    public int readInt(final int offset) {
        byte[] classBuffer = classFileBuffer;
        return ((classBuffer[offset] & 0xFF) << 24)
                | ((classBuffer[offset + 1] & 0xFF) << 16)
                | ((classBuffer[offset + 2] & 0xFF) << 8)
                | (classBuffer[offset + 3] & 0xFF);
    }

    /**
     * Reads a signed long value in this {@link ClassReader}. <i>This method is intended for
     * Attribute sub classes, and is normally not needed by class generators or adapters.</i>
     *
     * @param offset the start offset of the value to be read in this {@link ClassReader}.
     * @return the read value.
     */
    public long readLong(final int offset) {
        long l1 = readInt(offset);
        long l0 = readInt(offset + 4) & 0xFFFFFFFFL;
        return (l1 << 32) | l0;
    }

    /**
     * Reads a CONSTANT_Utf8 constant pool entry in this {@link ClassReader}. <i>This method is
     * intended for Attribute sub classes, and is normally not needed by class generators or
     * adapters.</i>
     *
     * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
     *                   value is the index of a CONSTANT_Utf8 entry in the class's constant pool table.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Utf8 entry.
     */
    // DontCheck(AbbreviationAsWordInName): can't be renamed (for backward binary compatibility).
    public String readUtf8(final int offset, final char[] charBuffer) {
        int constantPoolEntryIndex = readUnsignedShort(offset);
        if (offset == 0 || constantPoolEntryIndex == 0) {
            return null;
        }
        return readUtf(constantPoolEntryIndex, charBuffer);
    }

    /**
     * Reads a CONSTANT_Utf8 constant pool entry in {@link #classFileBuffer}.
     *
     * @param constantPoolEntryIndex the index of a CONSTANT_Utf8 entry in the class's constant pool
     *                               table.
     * @param charBuffer             the buffer to be used to read the string. This buffer must be sufficiently
     *                               large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Utf8 entry.
     */
    final String readUtf(final int constantPoolEntryIndex, final char[] charBuffer) {
        String value = constantUtf8Values[constantPoolEntryIndex];
        if (value != null) {
            return value;
        }
        int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
        return constantUtf8Values[constantPoolEntryIndex] =
                readUtf(cpInfoOffset + 2, readUnsignedShort(cpInfoOffset), charBuffer);
    }

    /**
     * Reads an UTF8 string in {@link #classFileBuffer}.
     *
     * @param utfOffset  the start offset of the UTF8 string to be read.
     * @param utfLength  the length of the UTF8 string to be read.
     * @param charBuffer the buffer to be used to read the string. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 string.
     */
    private String readUtf(final int utfOffset, final int utfLength, final char[] charBuffer) {
        int currentOffset = utfOffset;
        int endOffset = currentOffset + utfLength;
        int strLength = 0;
        byte[] classBuffer = classFileBuffer;
        while (currentOffset < endOffset) {
            int currentByte = classBuffer[currentOffset++];
            if ((currentByte & 0x80) == 0) {
                charBuffer[strLength++] = (char) (currentByte & 0x7F);
            } else if ((currentByte & 0xE0) == 0xC0) {
                charBuffer[strLength++] =
                        (char) (((currentByte & 0x1F) << 6) + (classBuffer[currentOffset++] & 0x3F));
            } else {
                charBuffer[strLength++] =
                        (char)
                                (((currentByte & 0xF) << 12)
                                        + ((classBuffer[currentOffset++] & 0x3F) << 6)
                                        + (classBuffer[currentOffset++] & 0x3F));
            }
        }
        return new String(charBuffer, 0, strLength);
    }

    /**
     * Reads a CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType, CONSTANT_Module or
     * CONSTANT_Package constant pool entry in {@link #classFileBuffer}. <i>This method is intended
     * for  Attribute sub classes, and is normally not needed by class generators or
     * adapters.</i>
     *
     * @param offset     the start offset of an unsigned short value in {@link #classFileBuffer}, whose
     *                   value is the index of a CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType,
     *                   CONSTANT_Module or CONSTANT_Package entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified constant pool entry.
     */
    private String readStringish(final int offset, final char[] charBuffer) {
        // Get the start offset of the cp_info structure (plus one), and read the CONSTANT_Utf8 entry
        // designated by the first two bytes of this cp_info.
        return readUtf8(cpInfoOffsets[readUnsignedShort(offset)], charBuffer);
    }

    /**
     * Reads a CONSTANT_Class constant pool entry in this {@link ClassReader}. <i>This method is
     * intended for  Attribute sub classes, and is normally not needed by class generators or
     * adapters.</i>
     *
     * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
     *                   value is the index of a CONSTANT_Class entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Class entry.
     */
    public String readClass(final int offset, final char[] charBuffer) {
        return readStringish(offset, charBuffer);
    }

    /**
     * Reads a CONSTANT_Module constant pool entry in this {@link ClassReader}. <i>This method is
     * intended for Attribute sub classes, and is normally not needed by class generators or
     * adapters.</i>
     *
     * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
     *                   value is the index of a CONSTANT_Module entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Module entry.
     */
    public String readModule(final int offset, final char[] charBuffer) {
        return readStringish(offset, charBuffer);
    }

    /**
     * Reads a CONSTANT_Package constant pool entry in this {@link ClassReader}. <i>This method is
     * intended for Attribute sub classes, and is normally not needed by class generators or
     * adapters.</i>
     *
     * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
     *                   value is the index of a CONSTANT_Package entry in class's constant pool table.
     * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
     *                   large. It is not automatically resized.
     * @return the String corresponding to the specified CONSTANT_Package entry.
     */
    public String readPackage(final int offset, final char[] charBuffer) {
        return readStringish(offset, charBuffer);
    }

}
