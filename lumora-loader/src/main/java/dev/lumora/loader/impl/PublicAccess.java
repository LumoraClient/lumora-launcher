package dev.lumora.loader.impl;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes every non-private class, inner class, field and method public. Needed when the game jar gets names that put
 * classes into other packages than the original ones (Yarn on 1.8.9 / 1.12.2): the obfuscated jar has all classes in
 * one package, so package-private and protected access between them is legal there and would fail after renaming.
 * Private members stay private (they are only used inside their own class).
 */
final class PublicAccess extends ClassVisitor {
    PublicAccess(ClassVisitor next) {
        super(Opcodes.ASM9, next);
    }

    static int widen(int access) {
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return access;
        }
        return (access & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, widen(access), name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, widen(access));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(widen(access), name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return super.visitMethod(widen(access), name, descriptor, signature, exceptions);
    }
}
