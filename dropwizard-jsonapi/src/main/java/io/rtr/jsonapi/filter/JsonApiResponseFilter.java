package io.rtr.jsonapi.filter;

import io.rtr.jsonapi.JSONAPI;
import io.rtr.jsonapi.JSONAPI.ApiDocumentBuilder;
import io.rtr.jsonapi.JSONAPI.ResourceObjectBuilder;
import io.rtr.jsonapi.annotation.ResourceObject;
import io.rtr.jsonapi.filter.ResourceMappingContext.Mapping;
import io.rtr.jsonapi.impl.ResourceObjectImpl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.uri.UriTemplate;

import com.google.common.collect.Lists;

public class JsonApiResponseFilter implements ContainerResponseFilter {
	private static final MediaType JSONAPI_MEDIATYPE = MediaType.valueOf("application/vnd.api+json");
	
	@Context
	private UriInfo uriInfo;
	
	@Context
	private ResourceMappingContext resourceMapping;
	
	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
		if (!isApplicable(requestContext)) {
			System.out.println("JSON API not requested");
			return;
		}
		System.out.println("HANDLING JSON API");
		
		final Object entity = responseContext.getEntity();
		if (entity != null && !uriInfo.getMatchedResources().isEmpty()) {
			List<Object> resources = uriInfo.getMatchedResources();
			Object resource = resources.get(0);
			Mapping requestResourceMapping= resourceMapping.getMapping(resource.getClass());

			ApiDocumentBuilder<Object> docBuilder = null;
			if (entity.getClass().isArray()) {
				final Object[] entityArray = (Object[])entity;
				final List<ResourceObjectImpl<Object>> resourceObjects = buildEntityList(Arrays.stream(entityArray), requestResourceMapping);

				docBuilder = JSONAPI.document(resourceObjects);
			} 
			else if (Collection.class.isAssignableFrom(entity.getClass())) {
				final Collection<?> entityCollection = (Collection<?>)entity;
				final List<ResourceObjectImpl<Object>> resourceObjects = buildEntityList(entityCollection.stream(), requestResourceMapping);
			
				docBuilder = JSONAPI.document(resourceObjects);
			} 
			else {
				final ResourceObjectImpl<Object> data = buildEntity(requestResourceMapping, entity);
				docBuilder = JSONAPI.document(data);
			}
			
			docBuilder.link("self", uriInfo.getRequestUri().toString());
			responseContext.setEntity(docBuilder.build(uriInfo));
		}
	}
	
	private ResourceObjectImpl buildEntity(final Mapping mapping, final Object entity) {
		final ResourceObject resourceAnnotation = entity.getClass().getDeclaredAnnotation(ResourceObject.class);
		Mapping m = mapping;
		if (resourceAnnotation != null) {
			m = resourceMapping.getMapping(resourceAnnotation.resource());
		}
		
		final ResourceObjectBuilder dataBuilder = JSONAPI.data(entity);
		if (m != null) {
			for (String key : m.getKeys()) {
				UriTemplate template = new UriTemplate(m.getPathTemplate(key));
				String uri = template.createURI(getId(entity));
				dataBuilder.link(key, uriInfo.getBaseUri().resolve(uri.substring(1)).toString());
			}
		}
		return dataBuilder.build();
	}
	
	private List<ResourceObjectImpl<Object>> buildEntityList(Stream<?> source, Mapping requestResourceMapping) {
		final List<ResourceObjectImpl<Object>> resourceObjects = Lists.newArrayList();
		source.forEach(obj -> {
			resourceObjects.add(buildEntity(requestResourceMapping, obj));
		});
		return resourceObjects;
	}
	
	private String getId(Object obj) {
		try {
			Field field = obj.getClass().getDeclaredField("id");
			field.setAccessible(true);
			Object id = field.get(obj);
			if (id instanceof String) {
				return (String)id;
			}
			return String.valueOf(id);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private boolean isApplicable(ContainerRequestContext requestContext) {
		return requestContext.getAcceptableMediaTypes()
				.stream()
				.anyMatch(m -> JSONAPI_MEDIATYPE.equals(m));
	}
}
