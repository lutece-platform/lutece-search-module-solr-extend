package fr.paris.lutece.plugins.solr.modules.extend.service;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import fr.paris.lutece.plugins.extend.service.extender.facade.ExtenderType;
import fr.paris.lutece.plugins.extend.service.extender.facade.IExtendableResourceResult;
import fr.paris.lutece.plugins.extend.service.extender.facade.InfoExtenderException;
import fr.paris.lutece.plugins.extend.service.extender.facade.ResourceExtenderServiceFacade;
import fr.paris.lutece.plugins.search.solr.indexer.ISolrItemExternalFieldProvider;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.portal.service.util.AppLogService;

public class ExtendSolrItemFieldProvider implements ISolrItemExternalFieldProvider {	 
    
	@Override
	public void provideFields(Collection<SolrItem> solrItems) {
		
		Map<String, List<SolrItem> > mapItem= solrItems.stream().collect(Collectors.groupingBy(SolrItem::getType));
		for( Entry<String, List<SolrItem>> entry: mapItem.entrySet()) {
				
			for ( ExtenderType<? extends IExtendableResourceResult> extender : ResourceExtenderServiceFacade.getListExtenderType( ) )
		    {
		       try {            	
		          	addExtendFields(entry.getValue( ), extender.getInfoExtenderByList(entry.getValue().stream().map(SolrItem::getIdResource).collect(Collectors.toList( )), entry.getKey( )));						
		        } catch (InfoExtenderException e) {
		                    	
		           	AppLogService.debug( e.getMessage( ), e );
				}
		      }       	
		}
	}
	/**
	 * Add for each solrItem the extender fields information 
	 * @param <T>
	 * 			the ExtendableResourceResult type (example: Hit)
	 * @param solrItems
	 * 			the solrItem
	 * @param extenderResultObject
	 * 			the ExtendableResourceResult object
	 */
	private <T extends IExtendableResourceResult>  void  addExtendFields( List<SolrItem> solrItems, List<T> extenderResultObject ) {
		
		if(CollectionUtils.isNotEmpty( extenderResultObject )) 
		{
			solrItems.forEach(item ->			
					setPropertyDescriptor(item,
							extenderResultObject.stream()
					.filter(bean -> 
					item.getIdResource( ).equals( bean.getIdExtendableResource( ) ) && item.getType().equals(bean.getExtendableResourceType( ))).collect(Collectors.toList())			
				)
			);	
		}
	}
	/**
	 * Invoke the read method properties descriptor to set the extender fields information to solrItem
	 * @param <T>
	 * 			the ExtendableResourceResult type (example: Hit)
	 * @param solrItem
	 * 			the solrItem
	 * @param extenderResultObject
	 * 			the ExtendableResourceResult object
	 */
	private <T extends IExtendableResourceResult> void setPropertyDescriptor( SolrItem solrItem, List<T> extenderResultObject ) 
	{		
		AtomicInteger count=new AtomicInteger( 0 );	 
		extenderResultObject.forEach( bean -> {
				List<PropertyDescriptor> beanGettersList = getBeanInfo( bean );
				beanGettersList.forEach( pd -> {
					
					if(!pd.getName().equals("idExtendableResource") && !pd.getName().equals("extendableResourceType")) {	
						
						String strName = null;
						if(count.get( ) == 0) {
							strName=pd.getName();
						}else {
							strName= pd.getName()+ "_" +count;
						}
						try 
						{
							if( Number.class.isAssignableFrom(pd.getPropertyType()) || pd.getPropertyType().getCanonicalName().equals("int") || pd.getPropertyType().getCanonicalName().equals("long"))
							{
								Object value= pd.getReadMethod()!=null?pd.getReadMethod().invoke(bean):null;
								if(value != null ) {
									solrItem.addDynamicField(strName, Long.valueOf(value.toString( )));	
								}
							}
							else if(Date.class.isAssignableFrom(pd.getPropertyType()))
							{
								Object value= pd.getReadMethod()!=null?pd.getReadMethod().invoke(bean):null;
								if( value != null ) 
								{
									solrItem.addDynamicField( strName, (Date) value );
								}
							}
							else
							{	
								Object value= pd.getReadMethod()!=null?pd.getReadMethod().invoke(bean):null;
								if( value != null ) 
								{
									solrItem.addDynamicField( strName, value.toString( ));	
								}
							}
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							
							AppLogService.error( e.getMessage( ), e );
						}
					}
				});
				count.incrementAndGet();
		});
	
	}

	/**
	 * Introspect on a Java bean and learn all about its properties, exposed
     * methods, below a given "stop" point.
     * If the BeanInfo class for a Java Bean has been previously Introspected
     * based on the same arguments, then the BeanInfo class is retrieved
     * from the BeanInfo cache.
	 * @param <T> the bean type
	 * @param bean the bean to be analyzed
	 * @return the BeanInfo for the bean
	 */
	private <T extends IExtendableResourceResult> List<PropertyDescriptor> getBeanInfo( T bean ){
		
		List<PropertyDescriptor> beanGettersList = new ArrayList<>();
		try {
			beanGettersList = Arrays.asList(
			        Introspector.getBeanInfo(bean.getClass(), Object.class)
			                .getPropertyDescriptors());
		} catch (IntrospectionException e) {
			
			AppLogService.error( e.getMessage( ), e );
		}
		return beanGettersList;
	}
	
}
