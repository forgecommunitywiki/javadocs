package fcw;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;

public abstract class IdentifyingVisitor extends VoidVisitorAdapter<IdentifyingVisitor.VisitContext> {
    private final SymbolResolver resolver;

    public IdentifyingVisitor(SymbolResolver resolver) {
        this.resolver = resolver;
    }

    public void visit(Visitable node) {
        node.accept(this, new VisitContext());
    }

    protected abstract void visitClass(TypeDeclaration<?> n, VisitContext ctx);

    @Override
    public void visit(AnnotationDeclaration n, VisitContext arg) {
        VisitContext ctx = arg.newFQN(n);
        visitClass(n, ctx);
        super.visit(n, ctx);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, VisitContext arg) {
        VisitContext ctx = arg.newFQN(n);
        visitClass(n, ctx);
        super.visit(n, ctx);
    }

    @Override
    public void visit(EnumDeclaration n, VisitContext arg) {
        VisitContext ctx = arg.newFQN(n);
        visitClass(n, ctx);
        super.visit(n, ctx);
    }

    @Override
    public void visit(ObjectCreationExpr n, VisitContext arg) {
        n.getAnonymousClassBody().ifPresent(l -> {
            VisitContext ctx = arg.anonClass();
            l.forEach(v -> v.accept(this, ctx));
        });
        // from super
        n.getArguments().forEach(p -> p.accept(this, arg));
        n.getScope().ifPresent(l -> l.accept(this, arg));
        n.getType().accept(this, arg);
        n.getTypeArguments().ifPresent(l -> l.forEach(v -> v.accept(this, arg)));
        n.getComment().ifPresent(l -> l.accept(this, arg));
    }

    //

    protected abstract void visitEnumConstant(EnumConstantDeclaration n, VisitContext ctx);

    @Override
    public void visit(EnumConstantDeclaration n, VisitContext arg) {
        if (n.getClassBody().isNonEmpty()) {
            visitEnumConstant(n, arg);
            super.visit(n, arg.anonClass());
        } else {
            visitEnumConstant(n, arg);
            super.visit(n, arg);
        }
    }

    //

    protected abstract void visitField(FieldDeclaration n, VisitContext ctx);

    @Override
    public void visit(FieldDeclaration n, VisitContext arg) {
        visitField(n, arg);
        super.visit(n, arg);
    }

    //

    protected abstract void visitMethod(MethodDeclaration n, String descriptor, VisitContext ctx);

    @Override
    public void visit(MethodDeclaration n, VisitContext arg) {
        visitMethod(n, ParserUtils.toDescriptor(resolver, n), arg);
        super.visit(n, arg);
    }

    //

    protected abstract void visitConstructor(ConstructorDeclaration n, String descriptor, VisitContext ctx);

    @Override
    public void visit(ConstructorDeclaration n, VisitContext arg) {
        visitConstructor(n, ParserUtils.toDescriptor(resolver, n), arg);
        super.visit(n, arg);
    }

    //

    protected abstract void visitAnnotationMember(AnnotationMemberDeclaration n, String descriptor, VisitContext ctx);

    @Override
    public void visit(AnnotationMemberDeclaration n, VisitContext arg) {
        visitAnnotationMember(n, ParserUtils.toDescriptor(resolver, n), arg);
        super.visit(n, arg);
    }

    public static class VisitContext {
        // Anonymous class counting starts at 1
        int anonymousClassCount = 1;
        String currentFQN;

        VisitContext newFQN(TypeDeclaration<?> type) {
            VisitContext ctx = new VisitContext();
            final ResolvedReferenceTypeDeclaration resolved = type.resolve();
            ctx.currentFQN = resolved.getPackageName() + "." + resolved.getClassName().replace('.', '$');
            return ctx;
        }

        VisitContext anonClass() {
            VisitContext ctx = new VisitContext();
            ctx.currentFQN = this.currentFQN + "$" + anonymousClassCount++;
            return ctx;
        }

        public String getQualifiedName() {
            return currentFQN;
        }
    }
}
