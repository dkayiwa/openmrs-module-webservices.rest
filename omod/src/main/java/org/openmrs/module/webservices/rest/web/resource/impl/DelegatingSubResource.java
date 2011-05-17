package org.openmrs.module.webservices.rest.web.resource.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.api.RestService;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.Resource;
import org.openmrs.module.webservices.rest.web.resource.api.SubResource;
import org.openmrs.module.webservices.rest.web.response.ObjectMismatchException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.util.OpenmrsUtil;

/**
 * Base implementation of a sub-resource of a DelegatingCrudResource that delegates to a domain object
 *
 * @param <T> type of the domain class we delegate to
 * @param <P> type of the parent that T is a sub-resource of
 * @param <PR> type of the resource of the parent
 */
public abstract class DelegatingSubResource<T, P, PR extends DelegatingCrudResource<P>> extends BaseDelegatingResource<T> implements SubResource {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * @param instance
	 * @return the parent of the given instance of this subresource
	 */
	public abstract P getParent(T instance);
	
	/**
	 * Sets the parent property on the given instance of this subresource
	 * 
	 * @param instance
	 * @param parent
	 */
	public abstract void setParent(T instance, P parent);
	
	/**
	 * Implementations should override this method to return a list of all instances that belong to
	 * the given parent
	 * 
	 * @throws ResponseException
	 */
	public abstract List<T> doGetAll(P parent, RequestContext context) throws ResponseException;
	
	/**
	 * @see Resource#getUri(java.lang.Object)
	 */
	@Override
	public String getUri(Object instance) {
		org.openmrs.module.webservices.rest.web.annotation.SubResource sub = getClass().getAnnotation(
		    org.openmrs.module.webservices.rest.web.annotation.SubResource.class);
		@SuppressWarnings("unchecked")
		T instanceAsT = (T) instance;
		String parentUri = getParentUri(instanceAsT);
		return parentUri + "/" + sub.path() + "/" + getUniqueId(instanceAsT);
	}
	
	/**
	 * @see SubResource#create(java.lang.String, SimpleObject, RequestContext)
	 */
	@Override
	public Object create(String parentUniqueId, SimpleObject post, RequestContext context) throws ResponseException {
		PR parentResource = getParentResource();
		P parent = parentResource.getByUniqueId(parentUniqueId);
		if (parent == null)
			throw new ObjectNotFoundException();
		T delegate = newDelegate();
		setParent(delegate, parent);
		ConversionUtil.setConvertedProperties(delegate, post);
		delegate = save(delegate);
		return ConversionUtil.convertToRepresentation(delegate, Representation.DEFAULT);
	}
	
	/**
	 * @see SubResource#retrieve(java.lang.String, java.lang.String, RequestContext)
	 */
	@Override
	public Object retrieve(String parentUniqueId, String uuid, RequestContext context) throws ResponseException {
		T delegate = getByUniqueId(uuid);
		if (delegate == null)
			throw new ObjectNotFoundException();
		testParent(delegate, parentUniqueId);
		return asRepresentation(delegate, context.getRepresentation());
	}
	
	/**
	 * Ensures that the uuid of the parent of delegate is the same as the passed in parentUniqueId
	 * 
	 * @param delegate
	 * @param parentUniqueId
	 * @throws ObjectMismatchException
	 */
	private void testParent(T delegate, String parentUniqueId) throws ObjectMismatchException {
		P parent = getParent(delegate);
		String test = getParentResource().getUniqueId(parent);
		if (!OpenmrsUtil.nullSafeEquals(test, parentUniqueId))
			throw new ObjectMismatchException(parentUniqueId + " does not match " + parent, null);
	}
	
	/**
	 * @see SubResource#update(java.lang.String, java.lang.String, SimpleObject, RequestContext)
	 */
	@Override
	public Object update(String parentUniqueId, String uuid, SimpleObject propertiesToUpdate, RequestContext context)
	                                                                                                                 throws ResponseException {
		T delegate = getByUniqueId(uuid);
		if (delegate == null)
			throw new ObjectNotFoundException();
		testParent(delegate, parentUniqueId);
		ConversionUtil.setConvertedProperties(delegate, propertiesToUpdate);
		delegate = save(delegate);
		return delegate;
	}
	
	/**
	 * @see org.openmrs.module.webservices.rest.web.resource.api.SubResource#delete(java.lang.String,
	 *      java.lang.String, java.lang.String,
	 *      org.openmrs.module.webservices.rest.web.RequestContext)
	 */
	@Override
	public void delete(String parentUniqueId, String uuid, String reason, RequestContext context) throws ResponseException {
		T delegate = getByUniqueId(uuid);
		if (delegate == null)
			throw new ObjectNotFoundException();
		testParent(delegate, parentUniqueId);
		delete(delegate, reason, context);
	}
	
	/**
	 * @see SubResource#purge(java.lang.String, java.lang.String, RequestContext)
	 */
	@Override
	public void purge(String parentUniqueId, String uuid, RequestContext context) throws ResponseException {
		T delegate = getByUniqueId(uuid);
		if (delegate == null) {
			// HTTP DELETE is idempotent, so if we can't find the object, we assume it's already deleted and return success
			return;
		}
		testParent(delegate, parentUniqueId);
		purge(delegate, context);
	}
	
	/**
	 * @see SubResource#getAll(java.lang.String, RequestContext)
	 */
	@Override
	public List<Object> getAll(String parentUniqueId, RequestContext context) throws ResponseException {
		P parent = getParentResource().getByUniqueId(parentUniqueId);
		List<Object> ret = new ArrayList<Object>();
		for (T match : doGetAll(parent, context))
			ret.add(asRepresentation(match, context.getRepresentation()));
		return ret;
	}
	
	private String getParentUri(T instance) {
		return getParentResource().getUri(getParent(instance));
	}
	
	@SuppressWarnings("unchecked")
	private PR getParentResource() {
		org.openmrs.module.webservices.rest.web.annotation.SubResource sub = getClass().getAnnotation(
		    org.openmrs.module.webservices.rest.web.annotation.SubResource.class);
		return (PR) Context.getService(RestService.class).getResource(sub.parent());
	}
	
}