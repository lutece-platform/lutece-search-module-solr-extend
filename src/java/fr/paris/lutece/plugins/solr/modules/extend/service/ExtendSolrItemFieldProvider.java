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
import java.util.Set;
import java.util.stream.Collectors;

import fr.paris.lutece.plugins.extend.service.extender.facade.ExtenderType;
import fr.paris.lutece.plugins.extend.service.extender.facade.IExtendableResourceResult;
import fr.paris.lutece.plugins.extend.service.extender.facade.InfoExtenderException;
import fr.paris.lutece.plugins.extend.service.extender.facade.ResourceExtenderServiceFacade;
import fr.paris.lutece.plugins.search.solr.indexer.ISolrItemExternalFieldProvider;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.portal.service.util.AppLogService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds to each indexed Solr item one child document per extender information of its resource. A child holds the scalar
 * properties of the information (numbers, booleans, texts, dates), never the resource keys nor the personal data of its
 * author.
 */
@ApplicationScoped
public class ExtendSolrItemFieldProvider implements ISolrItemExternalFieldProvider
{
    private static final Set<String> EXCLUDED_PROPERTIES = Set.of( "idExtendableResource", "extendableResourceType", "name", "email", "ipAddress",
            "luteceUserName", "encryptedLuteceUserName", "userGuid", "user", "avatar", "avatarUrl" );
    private static final Set<Class<?>> INTEGRAL_TYPES = Set.of( Long.class, Integer.class, Short.class, Byte.class, long.class, int.class,
            short.class, byte.class );

    /**
     * Adds the extender information of every registered extender type to the items that name their resource.
     *
     * @param solrItems
     *            the items to index
     */
    @Override
    public void provideFields( Collection<SolrItem> solrItems )
    {
        Map<String, List<SolrItem>> mapItem = solrItems.stream( ).filter( item -> item.getType( ) != null && item.getIdResource( ) != null )
                .collect( Collectors.groupingBy( SolrItem::getType ) );
        for ( Entry<String, List<SolrItem>> entry : mapItem.entrySet( ) )
        {
            List<String> listIdResource = entry.getValue( ).stream( ).map( SolrItem::getIdResource ).collect( Collectors.toList( ) );
            for ( ExtenderType<? extends IExtendableResourceResult> extender : ResourceExtenderServiceFacade.getListExtenderType( ) )
            {
                try
                {
                    addExtendFields( extender.getType( ), entry.getValue( ), extender.getInfoExtenderByList( listIdResource, entry.getKey( ) ) );
                }
                catch( InfoExtenderException e )
                {
                    AppLogService.debug( e.getMessage( ), e );
                }
            }
        }
    }

    /**
     * Adds to each item the extender information of its resource.
     *
     * @param <T>
     *            the extender information type
     * @param extenderType
     *            the extender type
     * @param solrItems
     *            the items
     * @param extenderResultObject
     *            the extender information of the resources of the items
     */
    private <T extends IExtendableResourceResult> void addExtendFields( String extenderType, List<SolrItem> solrItems, List<T> extenderResultObject )
    {
        if ( extenderResultObject != null && !extenderResultObject.isEmpty( ) )
        {
            solrItems.forEach( item -> setPropertyDescriptor( extenderType, item,
                    extenderResultObject.stream( ).filter( bean -> item.getIdResource( ).equals( bean.getIdExtendableResource( ) )
                            && item.getType( ).equals( bean.getExtendableResourceType( ) ) ).collect( Collectors.toList( ) ) ) );
        }
    }

    /**
     * Adds to the item one child document per extender information, holding its readable properties as dynamic fields.
     *
     * @param <T>
     *            the extender information type
     * @param extenderType
     *            the extender type
     * @param solrItem
     *            the item
     * @param extenderResultObject
     *            the extender information of the resource of the item
     */
    private <T extends IExtendableResourceResult> void setPropertyDescriptor( String extenderType, SolrItem solrItem, List<T> extenderResultObject )
    {
        extenderResultObject.forEach( bean -> {
            SolrItem item = new SolrItem( );
            item.setType( extenderType );
            getBeanInfo( bean ).forEach( pd -> addDynamicField( item, bean, pd ) );
            solrItem.addChildDocument( extenderType, item );
        } );
    }

    /**
     * Adds a readable scalar property of the bean to the item as a dynamic field: long for an integral number, date for a
     * date, text for any other number, a boolean, a character or a text. Excluded and non-scalar properties are not read.
     *
     * @param <T>
     *            the extender information type
     * @param item
     *            the child item
     * @param bean
     *            the extender information
     * @param pd
     *            the property
     */
    private <T extends IExtendableResourceResult> void addDynamicField( SolrItem item, T bean, PropertyDescriptor pd )
    {
        String strName = pd.getName( );
        Class<?> type = pd.getPropertyType( );
        if ( pd.getReadMethod( ) == null || EXCLUDED_PROPERTIES.contains( strName ) || !isScalar( type ) )
        {
            return;
        }
        try
        {
            Object value = pd.getReadMethod( ).invoke( bean );
            if ( value == null )
            {
                return;
            }
            if ( INTEGRAL_TYPES.contains( type ) )
            {
                item.addDynamicField( strName, ( (Number) value ).longValue( ) );
            }
            else
                if ( value instanceof Date )
                {
                    item.addDynamicField( strName, (Date) value );
                }
                else
                {
                    item.addDynamicField( strName, value.toString( ) );
                }
        }
        catch( IllegalAccessException | IllegalArgumentException | InvocationTargetException e )
        {
            AppLogService.error( e.getMessage( ), e );
        }
    }

    /**
     * Tells whether a property type is indexed: a primitive, a number, a boolean, a character, a text or a date.
     *
     * @param type
     *            the property type
     * @return true if a property of that type is indexed
     */
    private static boolean isScalar( Class<?> type )
    {
        return type != null && ( type.isPrimitive( ) || Number.class.isAssignableFrom( type ) || Boolean.class == type || Character.class == type
                || CharSequence.class.isAssignableFrom( type ) || Date.class.isAssignableFrom( type ) );
    }

    /**
     * Returns the properties of the bean, its class hierarchy below Object.
     *
     * @param <T>
     *            the bean type
     * @param bean
     *            the bean to introspect
     * @return the properties of the bean, empty when it cannot be introspected
     */
    private <T extends IExtendableResourceResult> List<PropertyDescriptor> getBeanInfo( T bean )
    {
        List<PropertyDescriptor> beanGettersList = new ArrayList<>( );
        try
        {
            beanGettersList = Arrays.asList( Introspector.getBeanInfo( bean.getClass( ), Object.class ).getPropertyDescriptors( ) );
        }
        catch( IntrospectionException e )
        {
            AppLogService.error( e.getMessage( ), e );
        }
        return beanGettersList;
    }
}
