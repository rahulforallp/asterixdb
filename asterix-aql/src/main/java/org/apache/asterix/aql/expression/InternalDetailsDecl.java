/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.aql.expression;

import java.util.List;

public class InternalDetailsDecl implements IDatasetDetailsDecl {
    private final List<List<String>> partitioningExprs;
    private final boolean autogenerated;
    private final boolean temp;
    private final List<String> filterField;

    public InternalDetailsDecl(List<List<String>> partitioningExpr, boolean autogenerated, List<String> filterField,
            boolean temp) {
        this.partitioningExprs = partitioningExpr;
        this.autogenerated = autogenerated;
        this.filterField = filterField;
        this.temp = temp;
    }

    public List<List<String>> getPartitioningExprs() {
        return partitioningExprs;
    }

    public boolean isAutogenerated() {
        return autogenerated;
    }

    @Override
    public boolean isTemp() {
        return temp;
    }

    public List<String> getFilterField() {
        return filterField;
    }

}