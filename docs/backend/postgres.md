# Backend module: `backend-postgres`

## Setup

```yaml
  Beer:
    backend: postgres
    table: dbeerpedia.beers
    keys:
      - field: identifier
```

### joinColumns

The `joinColumns` field configuration property contains an array of `joinColumn` objects. An `joinColumn` object indicates that a given column in the owner entity refers to a primary key in the reference entity:

```yaml
typeMapping:
  Beer:
    backend: postgres
    table: dbeerpedia.beers
    keys:
      - field: identifier
    fields:
      brewery:
        joinColumns:
          - name: brewery
            referencedField: identifier
            
  Brewery:
    backend: postgres
    table: dbeerpedia.breweries
    keys:
      - field: identifier
    fields:
      identifier:    
```

The above configuration example will use a foreign key linking the *Beer* entity with the primary key from the *Brewery* entity. The name of the foreign key column in the *Brewery* entity is specified by name property.

### mappedBy

Once we have defined the owning side of the relationship, DotWebStack already has all the information it needs to map that relationship in our database. To make this association bidirectional, all we'll have to do is to define the referencing side. The inverse or the referencing side simply maps to the owning side.

We can easily use the `mappedBy` configuration property to do so. So, let's define it:

```yaml
typeMapping:
  Beer:
    backend: postgres
    table: dbeerpedia.beers
    keys:
      - field: identifier
    fields:
      brewery:
        joinColumns:
          - name: brewery
            referencedField: identifier
            
  Brewery:
    backend: postgres
    table: dbeerpedia.breweries
    keys:
      - field: identifier
    fields:
      beers:
        mappedBy: brewery
```

Here, the value of mappedBy is the name of the association-mapping field on the owning side. With this, we have now established a bidirectional association between our *Brewery* and *Beer* entities.

### joinTable

An `joinTable` field configuration property can be used to make a many-to-many relation with a jointable. 

```yaml
typeMapping:
  Beer:
    backend: postgres
    table: dbeerpedia.beers
    keys:
      - field: identifier
    fields:
      ingredients:
        joinTable:
          name: dbeerpedia.beers_ingredients
          joinColumns:
            - name: beers_identifier
              referencedField: identifier
          inverseJoinColumns:
            - name: ingredients_identifier
              referencedField: identifier

  Ingredient:
    backend: postgres
    table: dbeerpedia.ingredients
    keys:
      - field: identifier
```

This association has two sides i.e. the owning side and the inverse side. In our example, the owning side is *Beer* so the join table is specified on the owning side by using the *joinTable* annotation in *Beer* class.

### aggregationOf

An `aggregationOf` field configuration can be used to aggregate a type with a many-to-many or one-to-many relation.
The `mappedBy` or `joinTable` configuration needs to be included.

Simplified configuration example:

```yaml
  Brewery:
    fields:
      beers:
        mappedBy: brewery
      beerAggregation:
        aggregationOf: Beer
        mappedBy: brewery

  Beer:
    fields:
      ingredients:
        joinTable:
          name: dbeerpedia.beers_ingredients
          joinColumns:
            - name: beers_identifier
              referencedField: identifier
          inverseJoinColumns:
            - name: ingredients_identifier
              referencedField: identifier
      ingredientAggregation:
        aggregationOf: Ingredient
        joinTable:
          name: dbeerpedia.beers_ingredients
          joinColumns:
            - name: beers_identifier
              referencedField: identifier
          inverseJoinColumns:
            - name: ingredients_identifier
              referencedField: identifier
```

## PostGIS

Geometry and Geography types, as part of the [PostGIS extension](https://postgis.net), are supported.

For an example implementation, see [example/example-postgres](https://github.com/dotwebstack/dotwebstack-framework/tree/v0.3/example/example-postgres).