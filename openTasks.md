# Open Topics

## Domain

- GroupState is not defined
- Should the round index start at 0 or 1?

## Rest

- api versioning (pathbased?)
- Pagination for getAllxxx methods
- PUT will only update resources, it will not create any new
- Should get create a new resource when it is not existing? like createOrGet?

## JPA

- strategy = GenerationType.IDENTITY will kill batch SQL statements. Meaning on saveAll() there will be one insert for
  each entity.
- Deleting a season should:
    - delete all events connected
    - throw an error when events are connected
- GROUP is a reserved keyword and should not be used for Tables, Hibernate will create the table automatically as Group_
  maybe replace it with Segment or something similar?

## DTOs

- minimal dto vs full infos vs partial infos, example:   
  returning an eventDto, should the season be included?
- Validation of Dtos (is there a need to validate that the path uuid is the same as the uuid within the dto? see:
  updateMappings) uuid within dto will be ignored anyway on update
- does the toEntity mapper need to map the uuid back to the entity? could be ignored, right?

