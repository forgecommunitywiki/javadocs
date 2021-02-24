package fcw;

import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.Printer;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

public class ParserUtils {
    public static final Printer PRINTER = new DefaultPrettyPrinter();

    static {
        PRINTER.getConfiguration().addOption(new DefaultConfigurationOption(
            ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY, 0)
        );
    }

    public static String toFQN(ResolvedReferenceTypeDeclaration decl) {
        String pkg = decl.getPackageName();
        return (pkg.isEmpty() ? "" : pkg + ".") + decl.getClassName().replace('.', '$');
    }

    public static String toDescriptor(SymbolResolver solver, MethodDeclaration method) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Parameter param : method.getParameters()) {
            builder.append(toDescriptor(solver, param.getType()));
        }
        builder.append(")");
        builder.append(toDescriptor(solver, method.getType()));
        return builder.toString();
    }

    public static String toDescriptor(SymbolResolver solver, ConstructorDeclaration constructor) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Parameter param : constructor.getParameters()) {
            builder.append(toDescriptor(solver, param.getType()));
        }
        builder.append(")V");
        return builder.toString();
    }

    public static String toDescriptor(SymbolResolver solver, AnnotationMemberDeclaration annotationMember) {
        return "()" + toDescriptor(solver, annotationMember.getType());
    }

    public static String toDescriptor(SymbolResolver solver, Type type) {
        ResolvedType resolved = solver.toResolvedType(type, ResolvedType.class);
        if (resolved.isTypeVariable()) {

            if (resolved.asTypeParameter().hasLowerBound()) {
                return toDescriptor(resolved.asTypeParameter().getLowerBound());
            }

            return "Ljava/lang/Object;";
        } else if (resolved.isArray()) {
            return "[" + toDescriptor(solver, type.asArrayType().getComponentType());
        }
        return type.toDescriptor();
    }

    public static String toDescriptor(ResolvedType type) {
        if (type.isArray()) {
            return "[" + toDescriptor(type);
        } else if (type.isPrimitive()) {
            return "L" + type.asPrimitive().getBoxTypeQName().replace('.', '/') + ";";
        } else if (type.isTypeVariable()) {
            if (type.asTypeParameter().hasLowerBound()) {
                return toDescriptor(type.asTypeParameter().getLowerBound());
            }
            return "Ljava/lang/Object;";
        }
        final ResolvedReferenceTypeDeclaration refDecl = type.asReferenceType().getTypeDeclaration()
            .orElseThrow(IllegalStateException::new);
        return String.format("L%s.%s;", refDecl.getPackageName().replace('.', '/'), refDecl.getClassName().replace('.', '$'));
    }
}
