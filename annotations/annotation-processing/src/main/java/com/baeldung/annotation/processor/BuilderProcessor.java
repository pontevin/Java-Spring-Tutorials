package com.baeldung.annotation.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.google.auto.service.AutoService;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class BuilderProcessor extends AbstractProcessor {

    private static final String ANNOTATION_CLASS_NAME = BuilderProperty.class.getName();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CLASS_NAME);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        try {
            for (TypeElement annotation : annotations) {
                checkAnnotation(annotation);
            }

            Set<? extends Element> methods = roundEnv.getElementsAnnotatedWith(BuilderProperty.class);
            for (Element method : methods) {
                checkAnnotatedMethod(method);
            }

            if (!methods.isEmpty()) {

                String className = ((TypeElement) methods.iterator().next().getEnclosingElement()).getQualifiedName().toString();

                Map<String, String> setterMap = methods.stream().collect(Collectors.toMap(setter -> setter.getSimpleName().toString(), setter -> ((ExecutableType) setter.asType()).getParameterTypes().get(0).toString()));

                try {
                    writeBuilderFile(className, setterMap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (ProcessingException e) {
            printError(e.getMessage(), e.getElement());
        }

        return true;
    }

    private void checkAnnotation(TypeElement annotation) throws ProcessingException {
        boolean validAnnotation = annotation.getQualifiedName().contentEquals(ANNOTATION_CLASS_NAME);
        if (!validAnnotation) {
            throw new ProcessingException(annotation, "Unknown annotation '%s'", annotation.getSimpleName().toString());
        }
    }

    private void checkAnnotatedMethod(Element method) throws ProcessingException {
        boolean validMethod =
                    ((ExecutableType) method.asType()).getParameterTypes().size() == 1 &&
                        method.getSimpleName().toString().startsWith("set");
        if (!validMethod)
            throw new ProcessingException(method, "@BuilderProperty must be applied to a setXxx method with a single argument");
    }

    private void printError(String message, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void writeBuilderFile(String className, Map<String, String> setterMap) throws IOException {

        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String builderClassName = className + "Builder";
        String builderSimpleClassName = builderClassName.substring(lastDot + 1);

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(builderClassName);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.print("public class ");
            out.print(builderSimpleClassName);
            out.println(" {");
            out.println();

            out.print("    private ");
            out.print(simpleClassName);
            out.print(" object = new ");
            out.print(simpleClassName);
            out.println("();");
            out.println();

            out.print("    public ");
            out.print(simpleClassName);
            out.println(" build() {");
            out.println("        return object;");
            out.println("    }");
            out.println();

            setterMap.entrySet().forEach(setter -> {
                String methodName = setter.getKey();
                String argumentType = setter.getValue();

                out.print("    public ");
                out.print(builderSimpleClassName);
                out.print(" ");
                out.print(methodName);

                out.print("(");

                out.print(argumentType);
                out.println(" value) {");
                out.print("        object.");
                out.print(methodName);
                out.println("(value);");
                out.println("        return this;");
                out.println("    }");
                out.println();
            });

            out.println("}");

        }
    }

}
