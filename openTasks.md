# Open Topics

## Domain

- ~~GroupState is not defined~~
- ~~Should the round index start at 0 or 1?~~ -> start at 0
- ~~Does every team have a matchday in a league? So there are 4 matchDays on the same day with a league of 8 teams? And
  for one season there would be 28 matchDays? what if a matchday would be the sum of all encounters (4), so there would
  be 7 matchDays with 4 encounters?~~ -> matchday stays, even for tournaments, later we could improve by updating the match with round
- remove ids, use uuids in database
- check if saving of uuid would be sufficient for entity relationships
- MatchEventType ``Goal`` vs ``Own_goal``?
- MatchEvent vs SetEvent?

## Rest

- api versioning (path based?)
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
  → replaced with Pool
- In a later state, team will come from dtfb api / coral, for now it will be stored here
- Do we need to query the JSON we store in MatchEvent? → we could use ``jsonb`` for postgres or ``json`` for mysql
- Constraint check, e.g. matches with existing matchEvents cant be edited with changing teams

## DTOs

- minimal dto vs full infos vs partial infos, example:   
  returning an eventDto, should the season be included?
- Validation of Dtos (is there a need to validate that the path uuid is the same as the uuid within the dto? see:
  updateMappings) uuid within dto will be ignored anyway on update
- does the toEntity mapper need to map the uuid back to the entity? could be ignored, right?
- minimal dto, if calling update, do i need to add relational uuids to the dto? or only when there is a change? if so,
  the create and update service methods need refactoring

## RestClient (External Api)

- Connection Timeout and Read timeout, how to set with spring boot 4? Officially it is
  ``spring.http.clients.connect-timeout``, but variable can not be resolved
