import java.io.File;
import java.io.PrintWriter;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxEditorParser;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.semanticweb.owlapi.util.AnnotationValueShortFormProvider;
import org.semanticweb.owlapi.util.OWLOntologyImportsClosureSetProvider;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;

public class Rndtrips
{
  int target_cnt;
  int extra_terms;
  int total_terms;
  String base_iri;
  String in_fname;
  String out_fname;
  Random rnd;

  public static void main(String [] args) throws Exception
  {
    Rndtrips r = new Rndtrips();
    r.run(args);
  }

  public void run(String [] args) throws Exception
  {
    if ( !parse_cmdline( args ) )
      return;

    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    File kbfile;
    OWLOntology ont;

    try
    {
      kbfile = new File( in_fname );
    }
    catch( Exception e )
    {
      Out( "Could not open file: " + in_fname );
      return;
    }

    Out( "Attempting to parse ontology..." );

    try
    {
      ont = manager.loadOntologyFromOntologyDocument(new FileDocumentSource(kbfile));
    }
    catch( Exception e )
    {
      Out( "File loaded, but could not parse as ontology: " + in_fname );
      return;
    }

    IRI iri = manager.getOntologyDocumentIRI(ont);

    OWLDataFactory df = OWLManager.getOWLDataFactory();
    Set<OWLOntology> onts = ont.getImportsClosure();

    Out( "Ontology successfully loaded and parsed." );
    Out( "" );

    Out( "Reading ontology's import closure..." );

    List<String> all_terms = new ArrayList<String>();

    for ( OWLOntology o : onts )
    {
      for ( OWLClass c : o.getClassesInSignature() )
      {
        String cId = c.toStringID();

        for ( OWLIndividual ind : c.getIndividuals(o) )
        {
          if ( !(ind instanceof OWLNamedIndividual) )
            continue;

          String iId = ind.asOWLNamedIndividual().getIRI().toString();

          if ( iId.equals("") )
            continue;

          all_terms.add( iId );
        }

        if ( cId.equals("") )
          continue;

        all_terms.add( cId );
      }
    }

    Out( "Finished reading ontology's import closure." );

    if ( all_terms.size() == 0 && extra_terms == 0 )
    {
      Out( "Error: no classes found in the input ontology or its imports closure" );
      return;
    }

    rnd = new Random();

    if ( extra_terms > 0 )
    {
      Out( "Generating extra terms in addition to the ontology..." );

      for ( int i = 0; i < extra_terms; i++ )
        add_extra_term( all_terms );
    }

    total_terms = all_terms.size();

    Out( "Opening output file..." );

    PrintWriter writer;

    try
    {
      writer = new PrintWriter( out_fname, "UTF-8" );
    }
    catch( Exception e )
    {
      Out( "Error: Could not open "+out_fname+" for writing" );
      return;
    }

    for ( int i = 0; i < target_cnt; i++ )
      add_triple( all_terms, writer );

    writer.close();

    Out( "Done." );
  }

  private void add_extra_term( List<String> terms )
  {
    String term = base_iri + rnd.nextInt(1000000000);

    terms.add( term );
  }

  private void add_triple( List<String> terms, PrintWriter writer )
  {
    String subj = terms.get(rnd.nextInt(total_terms));
    String pred = terms.get(rnd.nextInt(total_terms));
    String obj  = terms.get(rnd.nextInt(total_terms));

    writer.println( "<" + subj + "> <" + pred + "> <" + obj + "> ." );
  }

  private boolean parse_cmdline( String [] args )
  {
    base_iri = "http://open-physiology.org/random_triples#";
    extra_terms = 0;

    if ( args.length < 3 )
    {
      if ( args.length >= 1 )
      {
        if ( removeTrailingDashes( args[0].toLowerCase() ).equals("help") )
          return display_help();
        if ( removeTrailingDashes( args[0].toLowerCase() ).equals("about") )
          return display_about();
      }

      return send_syntax( true );
    }

    for ( int i = 0; i < args.length - 3; i++ )
    {
      String arg = removeTrailingDashes( args[i].toLowerCase().trim() );

      if ( arg.equals("help") )
        return display_help();

      if ( arg.equals("about") )
        return display_about();

      if ( arg.equals("base") )
      {
        if ( ++i < args.length-3 )
        {
          base_iri = args[i].trim();
          Out( "Program has been set to use \"" + base_iri + "\" as base IRI" );

          continue;
        }
        else
          return send_syntax( true );
      }

      if ( arg.equals("extras") )
      {
        if ( ++i < args.length-3 )
        {
          extra_terms = string_to_int( args[i] );

          if ( extra_terms < 0 )
          {
            Out( "The number of extra terms to use must be a non-negative integer" );
            return false;
          }

          Out( "The random triple generator will draw from "+extra_terms+" extra IRIs in addition to the classes in the ontology" );
          continue;
        }
        else
        {
          Out( "How many extra IRIs do you want the random triple generator to pull from, in addition to the classes in your ontology?" );
          return false;
        }
      }

      Out( "Unrecognized command: " + arg );
      Out( "For help: java Rndtrips help" );
      return false;
    }

    target_cnt = string_to_int( args[args.length-3] );

    if ( target_cnt < 1 )
    {
      Out( "Number of triples must be a positive integer" );
      Out( "" );
      return send_syntax( true );
    }

    Out( "Program has been set to generate "+target_cnt+" random triples." );

    in_fname = args[args.length-2];
    Out( "Program will attempt to load ontology at location:" );
    Out( "  " + in_fname );

    out_fname = args[args.length-1];
    Out( "Program will attempt to save results to location:" );
    Out( "  " + out_fname );

    return true;
  }

  private boolean send_syntax( boolean refer_to_help )
  {
    Out( "Syntax:" );
    Out( "  java Rndtrips [optional arguments] <number of triples> <OWL file> <output file>" );

    if ( refer_to_help )
      Out( "For help: java Rntrips help" );

    return false;
  }

  private boolean display_about()
  {
    Out( "Random triples" );
    Out( "Written by Sam Alexander for the Farr Institute" );
    Out( "" );
    Out( "Generates random triples using a mix of nonsense terms and" );
    Out( "terms from an ontology supplied by the user." );
    Out( "" );
    Out( "On github: https://github.com/semitrivial/random_triples" );
    Out( "" );
    Out( "For help:" );
    Out( "  java Rndtrips help" );

    return false;
  }

  private boolean display_help()
  {
    send_syntax( false );
    Out( "" );
    Out( "Optional arguments:" );
    Out( "  base" );
    Out( "    Specify a base IRI to use for non-ontology IRIs." );
    Out( "    Default: http://open-physiology.org/random_triples#" );
    Out( "  extras" );
    Out( "    Specify the number of extra terms, in addition to" );
    Out( "    terms from the ontology, to randomly choose from." );
    Out( "    These additional terms will be randomly generated" );
    Out( "    and will be built using the base IRI (above)." );
    Out( "    Default: 0" );
    Out( "" );
    Out( "---------------------------------------------------" );
    Out( "Example usage:" );
    Out( "java Rndtrips extras 1000 base http://eg.com/rand_trips# 5000 /usr/mykb.owl out.n3" );
    Out( "" );

    return false;
  }

  private void Out( String txt )
  {
    System.out.println( txt );
  }

  int string_to_int( String s )
  {
    try
    {
      return Integer.parseInt( s );
    }
    catch (Exception e)
    {
      return -1;
    }
  }

  String removeTrailingDashes( String arg )
  {
    if ( arg.length() > 2 && arg.substring(0,2).equals("--") )
      return arg.substring(2);

    if ( arg.length() > 1 && arg.substring(0,1).equals("-") )
      return arg.substring(1);

    return arg;
  }
}
