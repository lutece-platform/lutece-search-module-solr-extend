/*
 * Copyright (c) 2002-2026, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.solr.modules.extend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import fr.paris.lutece.plugins.extend.service.extender.facade.ExtenderType;
import fr.paris.lutece.plugins.extend.service.extender.facade.IExtendableResourceResult;
import fr.paris.lutece.plugins.extend.service.extender.facade.ResourceExtenderServiceFacade;
import fr.paris.lutece.plugins.search.solr.indexer.ISolrItemExternalFieldProvider;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.test.LuteceTestCase;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Extender information added to indexed Solr items as child documents.
 */
public class ExtendSolrItemFieldProviderTest extends LuteceTestCase
{
    private static final String EXTENDER_TYPE = "unit_test_solr_extend";
    private static final String RESOURCE_TYPE = "unit_test_solr_extend_resource";
    private static final String CHILD_KEY = EXTENDER_TYPE + SolrItem.FIELD_CHILD_DOCUMENTS;
    private static final Date DATE_UPDATE = new Date( 1_700_000_000_000L );

    @Inject
    @Any
    private Instance<ISolrItemExternalFieldProvider> _providers;

    /**
     * The provider is the field provider plugin-solr finds through CDI; it adds one child document per extender
     * information to the item of its resource, with its integral numbers as long, its dates as date and its other
     * scalars as text, leaves out personal data and non-scalar properties, and leaves alone an item that names no
     * resource.
     */
    @Test
    public void testProvideFields( )
    {
        ResourceExtenderServiceFacade.addExtenderType( new ExtenderType<>( UnitTestResult.class, EXTENDER_TYPE,
                ( strId, strType ) -> List.of( new UnitTestResult( strId, strType ) ),
                ( listId, strType ) -> listId.stream( ).map( strId -> new UnitTestResult( strId, strType ) ).collect( Collectors.toList( ) ),
                ( strId, strType ) -> "3", ( strId, strType ) -> "3" ) );

        List<ISolrItemExternalFieldProvider> listProviders = _providers.stream( ).filter( ExtendSolrItemFieldProvider.class::isInstance ).toList( );
        assertEquals( 1, listProviders.size( ), "provider found by plugin-solr" );

        SolrItem item = newItem( "42" );
        SolrItem itemWithoutResource = newItem( null );
        listProviders.get( 0 ).provideFields( List.of( item, itemWithoutResource ) );

        assertFalse( itemWithoutResource.hasChildDocuments( ), "no child for an item without resource" );
        Map<String, Collection<SolrItem>> mapChildren = item.getChildDocuments( );
        assertNotNull( mapChildren, "children of the item" );
        Collection<SolrItem> children = mapChildren.get( CHILD_KEY );
        assertNotNull( children, "children of the test extender" );
        assertEquals( 1, children.size( ), "one child per extender information" );

        SolrItem child = children.iterator( ).next( );
        Map<String, Object> mapFields = child.getDynamicFields( );
        assertEquals( EXTENDER_TYPE, child.getType( ), "child type" );
        assertEquals( 3L, mapFields.get( "count" + SolrItem.DYNAMIC_LONG_FIELD_SUFFIX ), "number as long" );
        assertEquals( 42L, mapFields.get( "total" + SolrItem.DYNAMIC_LONG_FIELD_SUFFIX ), "Long as long" );
        assertEquals( DATE_UPDATE, mapFields.get( "dateUpdate" + SolrItem.DYNAMIC_DATE_FIELD_SUFFIX ), "date as date" );
        assertEquals( "three", mapFields.get( "label" + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX ), "text as text" );
        assertNull( mapFields.get( "idExtendableResource" + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX ), "resource id not copied" );
        assertNull( mapFields.get( "extendableResourceType" + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX ), "resource type not copied" );
        assertEquals( "3.5", mapFields.get( "average" + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX ), "decimal number as text" );
        assertTrue( mapFields.keySet( ).stream( ).noneMatch( key -> key.startsWith( "missing" ) ), "null property not copied" );
        assertTrue( mapFields.keySet( ).stream( ).noneMatch( key -> key.startsWith( "email" ) ), "e-mail not copied" );
        assertTrue( mapFields.keySet( ).stream( ).noneMatch( key -> key.startsWith( "name" ) ), "author name not copied" );
        assertTrue( mapFields.keySet( ).stream( ).noneMatch( key -> key.startsWith( "tags" ) ), "non-scalar property not copied" );
    }

    /**
     * Builds an item of the test resource type.
     *
     * @param strIdResource
     *            the resource id, null for none
     * @return the item
     */
    private SolrItem newItem( String strIdResource )
    {
        SolrItem item = new SolrItem( );
        item.setType( RESOURCE_TYPE );
        item.setIdResource( strIdResource );
        return item;
    }

    /**
     * Extender information of the test extender type.
     */
    public static class UnitTestResult implements IExtendableResourceResult
    {
        private final String _strIdExtendableResource;
        private final String _strExtendableResourceType;

        /**
         * Builds the information of a resource.
         *
         * @param strIdExtendableResource
         *            the resource id
         * @param strExtendableResourceType
         *            the resource type
         */
        public UnitTestResult( String strIdExtendableResource, String strExtendableResourceType )
        {
            _strIdExtendableResource = strIdExtendableResource;
            _strExtendableResourceType = strExtendableResourceType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIdExtendableResource( )
        {
            return _strIdExtendableResource;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getExtendableResourceType( )
        {
            return _strExtendableResourceType;
        }

        /**
         * Returns the count.
         *
         * @return 3
         */
        public int getCount( )
        {
            return 3;
        }

        /**
         * Returns the total.
         *
         * @return 42
         */
        public Long getTotal( )
        {
            return 42L;
        }

        /**
         * Returns the date of the last update.
         *
         * @return the date
         */
        public Date getDateUpdate( )
        {
            return DATE_UPDATE;
        }

        /**
         * Returns the label.
         *
         * @return three
         */
        public String getLabel( )
        {
            return "three";
        }

        /**
         * Returns the average.
         *
         * @return 3.5
         */
        public Double getAverage( )
        {
            return 3.5;
        }

        /**
         * Returns the e-mail of the author.
         *
         * @return an e-mail
         */
        public String getEmail( )
        {
            return "author@e2e.local";
        }

        /**
         * Returns the name of the author.
         *
         * @return a name
         */
        public String getName( )
        {
            return "Author";
        }

        /**
         * Returns the tags.
         *
         * @return two tags
         */
        public List<String> getTags( )
        {
            return List.of( "a", "b" );
        }

        /**
         * Returns a property without value.
         *
         * @return null
         */
        public String getMissing( )
        {
            return null;
        }
    }
}
