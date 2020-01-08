/*
* Copyright (c) 2019 Contributors to the Eclipse Foundation
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI;

public class Operations {
    public static org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryQuery query(org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryQueryDefinition queryDef) {
        StringBuilder queryString = new StringBuilder("{");
        org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryQuery query = new org.eclipse.microprofile.graphql.superhero_client.SuperHeroAPI.QueryQuery(queryString);
        queryDef.define(query);
        queryString.append('}');
        return query;
    }

    public static MutationQuery mutation(MutationQueryDefinition queryDef) {
        StringBuilder queryString = new StringBuilder("mutation{");
        MutationQuery query = new MutationQuery(queryString);
        queryDef.define(query);
        queryString.append('}');
        return query;
    }
}