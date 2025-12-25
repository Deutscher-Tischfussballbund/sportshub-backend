# TODOs

- Pagination for getAllxxx methods
- Validation of Dtos (is there a need to validate that the path uuid is the same as the uuid within the dto? see:
  updateMappings) uuid within dto will be ignored anyway on update
- strategy = GenerationType.IDENTITY will kill batch SQL statements. Meaning on saveAll() there will be one insert for
  each entity.
- PUT will only update resources, it will not create any new
- Should get create a new resource when it is not existing? like createOrGet?
