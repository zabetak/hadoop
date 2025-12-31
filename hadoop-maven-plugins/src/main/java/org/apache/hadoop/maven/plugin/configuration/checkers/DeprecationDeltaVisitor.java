package org.apache.hadoop.maven.plugin.configuration.checkers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class DeprecationDeltaVisitor extends ClassVisitor {
    private static final String DEPRECATION_DELTA = "org/apache/hadoop/conf/Configuration$DeprecationDelta";
    private final Set<String> deprecatedProps;

    public DeprecationDeltaVisitor(Set<String> deprecatedProps) {
        super(Opcodes.ASM9);
        this.deprecatedProps = deprecatedProps;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {

            // temp stack to collect strings before constructor
            private final List<String> tempStrings = new ArrayList<>();
            private boolean newDeprecationDelta = false;

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW && type.equals(DEPRECATION_DELTA)) {
                    newDeprecationDelta = true;
                    tempStrings.clear();
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof String && newDeprecationDelta) {
                    tempStrings.add((String) value);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                // look for constructor call
                if (newDeprecationDelta
                        && opcode == Opcodes.INVOKESPECIAL
                        && DEPRECATION_DELTA.equals(owner)
                        && methodName.equals("<init>")) {

                    if (!tempStrings.isEmpty()) {
                        // first string argument is usually the deprecated key
                        deprecatedProps.add(tempStrings.get(0));
                    }
                    newDeprecationDelta = false;
                    tempStrings.clear();
                }
            }
        };
    }
}
