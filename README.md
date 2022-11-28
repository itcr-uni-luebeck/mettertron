# Mettertron

*Mettertron* is a system for performing code validation and code mappings via definitions defined in an Metadata Repository (MDR), while the required terminology is maintained in a HL7 FHIR Terminology server (TS).

This represents a paradigm shift from the status quo. Previously, MDRs required that terminology is maintained in the MDR itself, and often don't support mapping operations. Our system, which is closely aligned to the ISO/TS 21526 standard for MDRs, delegates the process of terminology maintenance to a TS, which is much better suited to this job than an MDR can be.

The work in this repository is the subject of a paper submitted to the MedInfo 2023 conference, held in Sydney, Australia.

For quoting in the meantime, please refer to the Zenodo DOI linked in the badge above this text.

## Usage

The configuration of this tool is accomplished via HOCON configuration properties. The file [`resources/application.conf`](resources/application.conf) is the main configuration file. Aside from the caching functionality, you may want to change the properties in the `mdr-attributes` key. These properties state which properties in the MDR definitions contain the definitions needed for the FHIR functionality. 

The `terminology` key refers to your FHIR TS endpoint.

Aside from that file, you'll also need to create `resources/mdr.conf`. For this, we provide an example file, which you'll need to copy to the filename [`mdr.conf`](resources/mdr.example.conf). `mdr.conf` should, of course, never wind up in a Git repo, so please be careful.

After firing up the application, you'll be able to visit the Swagger UI at http://localhost:4200. There are a number of endpoints implemented in the REST API; but most endpoints only expose functionality needed for the main event. That is available under the `magic` endpoint. There are two endpoints, `$validate-code` and `$translate`, which mirror the respective HL7 FHIR endpoints. They need the identification of the requested MDR definition, and some additional parameters - please refer to the explanations in the Swagger definition for more details.

Obviously, you also need appropriate definitions in the MDR. Create the properties from `mdr-attributes` (this applies only to Kairos CentraXX MDR), and then create the definitions as appropriate (use slots for Samply MDR and the Data Element Hub). Then, you should be able to try out the tool.

## Implementation

The system is implemented in Kotlin, using the Ktor framework. All functionality is exposed using a HTTP API. We use Ktor 1.x (instead of the newer 2.x series), since there is a Swagger middleware available for Ktor 1.x, but currently not for 2.x. For FHIR operations, we use the HAPI FHIR library, and perform HTTP operations using the Ktor-provided HTTP client, instead of the HAPI FHIR client.

## Hacking

The code in this repository is licensed under the GNU Affero General Public License (GNU AGPL). Please keep the source code of this software open and feed back changes to the community!
