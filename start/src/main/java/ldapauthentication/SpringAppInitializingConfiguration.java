package ldapauthentication;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.annotations.AbstractDiscoverableAnnotationHandler;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.annotations.AnnotationDecorator;
import org.eclipse.jetty.annotations.AnnotationParser;
import org.eclipse.jetty.annotations.ClassNameResolver;
import org.eclipse.jetty.annotations.WebFilterAnnotationHandler;
import org.eclipse.jetty.annotations.WebListenerAnnotationHandler;
import org.eclipse.jetty.annotations.WebServletAnnotationHandler;
import org.eclipse.jetty.annotations.AnnotationParser.DiscoverableAnnotationHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * This solution was drawn from http://stackoverflow.com/questions/13222071/spring-3-1-webapplicationinitializer-embedded-jetty-8-annotationconfiguration
 */
public class SpringAppInitializingConfiguration extends AnnotationConfiguration {
	
	@Override
	public void configure(WebAppContext context) throws Exception {
		boolean metadataComplete = context.getMetaData().isMetaDataComplete();
		context.addDecorator(new AnnotationDecorator(context));

		AnnotationParser parser = null;
		if (!metadataComplete) {
			if (context.getServletContext().getEffectiveMajorVersion() >= 3 || context.isConfigurationDiscovered()) {
				_discoverableAnnotationHandlers.add(new WebServletAnnotationHandler(context));
				_discoverableAnnotationHandlers.add(new WebFilterAnnotationHandler(context));
				_discoverableAnnotationHandlers.add(new WebListenerAnnotationHandler(context));
			}
		}

		createServletContainerInitializerAnnotationHandlers(context, getNonExcludedInitializers(context));

		if (!_discoverableAnnotationHandlers.isEmpty() || _classInheritanceHandler != null || !_containerInitializerAnnotationHandlers.isEmpty()) {
			parser = new AnnotationParser() {

				@Override
				public void parse(Resource dir, ClassNameResolver resolver) throws Exception {
					if (dir.isDirectory()) {
						super.parse(dir, resolver);
					} else {
						super.parse(dir.getURI(), resolver);
					}
				}

			};

			parse(context, parser);

			for (DiscoverableAnnotationHandler handler: _discoverableAnnotationHandlers) {
				context.getMetaData().addDiscoveredAnnotations(((AbstractDiscoverableAnnotationHandler)handler).getAnnotationList());
			}

		}
	}

	final private void parse(final WebAppContext context, AnnotationParser parser) throws Exception {
		List<Resource> resources = getResources(getClass().getClassLoader());
		for (Resource resource : resources) {
			if (resource == null) {
				return;
			}
			parser.clearHandlers();
			for (DiscoverableAnnotationHandler handler : _discoverableAnnotationHandlers) {
				if (handler instanceof AbstractDiscoverableAnnotationHandler) {
					((AbstractDiscoverableAnnotationHandler)handler).setResource(null);
				}
			}
			parser.registerHandlers(_discoverableAnnotationHandlers);
			parser.registerHandler(_classInheritanceHandler);
			parser.registerHandlers(_containerInitializerAnnotationHandlers);

			parser.parse(resource, new ClassNameResolver() {
				@Override
				public boolean isExcluded(String name) {
					if (context.isSystemClass(name)) return true;
					if (context.isServerClass(name)) return false;
					return false;
				}

				@Override
				public boolean shouldOverride(String name) {
					if (context.isParentLoaderPriority()) {
						return false;
					}
					return true;
				}
			});
		}
	}

	@SuppressWarnings("serial")
	final private List<Resource> getResources(final ClassLoader classLoader) throws IOException {
		if (classLoader instanceof URLClassLoader) {
			return new ArrayList<Resource>() {{
				for (URL url : ((URLClassLoader)classLoader).getURLs()) {
					add(Resource.newResource(url));
				}
			}};
		}
		return Collections.emptyList();
	}
}
