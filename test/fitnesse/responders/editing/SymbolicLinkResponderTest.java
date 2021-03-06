// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.responders.editing;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static util.RegexTestCase.assertSubString;

import fitnesse.FitNesseContext;
import fitnesse.http.MockRequest;
import fitnesse.http.Response;
import fitnesse.http.SimpleResponse;
import fitnesse.testutil.FitNesseUtil;
import fitnesse.wiki.PageData;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.SymbolicPage;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageProperty;
import fitnesse.wiki.WikiPageUtil;
import fitnesse.wiki.fs.FileSystemPage;
import fitnesse.wiki.fs.InMemoryPage;
import fitnesse.wiki.fs.MemoryFileSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SymbolicLinkResponderTest {
  private WikiPage root;
  private WikiPage pageOne;
  private WikiPage childTwo;
  private MockRequest request;
  private SymbolicLinkResponder responder;
  private MemoryFileSystem fileSystem;
  private FitNesseContext context;

  @Before
  public void setUp() throws Exception {
    fileSystem = new MemoryFileSystem();
    root = InMemoryPage.makeRoot("RooT", fileSystem);          //#  root
    pageOne = WikiPageUtil.addPage(root, PathParser.parse("PageOne"), "");       //#    |--PageOne
    WikiPageUtil.addPage(pageOne, PathParser.parse("ChildOne"), "");   //#    |    `--ChildOne
    WikiPage pageTwo = WikiPageUtil.addPage(root, PathParser.parse("PageTwo"), "");
    childTwo = WikiPageUtil.addPage(pageTwo, PathParser.parse("ChildTwo"), "");   //#         |--ChildTwo
    WikiPageUtil.addPage(pageTwo, PathParser.parse("ChildThree"), ""); //#         `--ChildThree

    request = new MockRequest();
    request.setResource("PageOne");
    context = FitNesseUtil.makeTestContext(root);
    responder = new SymbolicLinkResponder(fileSystem);
  }

  private void reloadPages() {
    pageOne = root.getChildPage("PageOne");
    WikiPage pageTwo = root.addChildPage("PageTwo");
    childTwo = pageTwo.addChildPage("ChildTwo");
  }


  private Response invokeResponder() throws Exception {
    Response response = responder.makeResponse(context, request);
    reloadPages();
    return response;
  }

  @After
  public void tearDown() throws Exception {
    FitNesseUtil.destroyTestContext(context);
  }

  @Test
  public void testSubmitGoodForm() throws Exception {
    executeSymbolicLinkTestWith("SymLink", "PageTwo");
  }

  @Test
  public void testShouldTrimSpacesOnLinkPath() throws Exception {
    executeSymbolicLinkTestWith("SymLink", "    PageTwo   ");
  }

  @Test
  public void testShouldTrimSpacesOnLinkName() throws Exception {
    executeSymbolicLinkTestWith("   SymLink   ", "PageTwo");
  }

  private void executeSymbolicLinkTestWith(String linkName, String linkPath) throws Exception {
    request.addInput("linkName", linkName);
    request.addInput("linkPath", linkPath);
    Response response = invokeResponder();

    checkPageOneRedirectToProperties(response);

    WikiPage symLink = pageOne.getChildPage("SymLink");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testSubmitGoodFormToSiblingChild() throws Exception {
    executeSymbolicLinkTestWith("SymLink", "PageTwo.ChildTwo");
  }

  @Test
  public void testSubmitGoodFormToChildSibling() throws Exception {
    request.setResource("PageTwo.ChildTwo");
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "ChildThree");
    Response response = invokeResponder();

    checkChildTwoRedirectToProperties(response);

    WikiPage symLink = childTwo.getChildPage("SymLink");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testSubmitGoodFormToAbsolutePath() throws Exception {
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", ".PageTwo");
    Response response = invokeResponder();

    checkPageOneRedirectToProperties(response);

    WikiPage symLink = pageOne.getChildPage("SymLink");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testSubmitGoodFormToSubChild() throws Exception {
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", ">ChildOne");
    Response response = invokeResponder();

    checkPageOneRedirectToProperties(response);

    SymbolicPage symLink = (SymbolicPage) (pageOne.getChildPage("SymLink"));
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testSubmitGoodFormToSibling() throws Exception {
    request.addInput("linkName", "SymTwo");
    request.addInput("linkPath", "PageTwo");
    Response response = invokeResponder();

    checkPageOneRedirectToProperties(response);

    WikiPage symLink = pageOne.getChildPage("SymTwo");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testSubmitGoodFormToBackwardRelative() throws Exception {
    request.setResource("PageTwo.ChildTwo");
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "<PageTwo.ChildThree");
    Response response = invokeResponder();

    checkChildTwoRedirectToProperties(response);

    WikiPage symLink = childTwo.getChildPage("SymLink");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());
  }

  @Test
  public void testRemoval() throws Exception {
    PageData data = pageOne.getData();
    WikiPageProperty symLinks = data.getProperties().set(SymbolicPage.PROPERTY_NAME);
    symLinks.set("SymLink", "PageTwo");
    pageOne.commit(data);
    assertNotNull(pageOne.getChildPage("SymLink"));

    request.addInput("removal", "SymLink");
    Response response = invokeResponder();
    checkPageOneRedirectToProperties(response);

    assertNull(pageOne.getChildPage("SymLink"));
  }

  @Test
  public void testRename() throws Exception {
    prepareSymlinkOnPageOne();
    request.addInput("newname", "NewLink");
    Response response = invokeResponder();
    checkPageOneRedirectToProperties(response);

    assertNotNull(pageOne.getChildPage("NewLink"));
  }

  @Test
  public void linkNameShouldBeAValidWikiWordWhenRenaming() throws Exception {
    prepareSymlinkOnPageOne();
    request.addInput("newname", "New+link");
    Response response = invokeResponder();

    assertEquals(412, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("WikiWord", content);
  }

  private void prepareSymlinkOnPageOne() {
    PageData data = pageOne.getData();
    WikiPageProperty symLinks = data.getProperties().set(SymbolicPage.PROPERTY_NAME);
    symLinks.set("SymLink", "PageTwo");
    pageOne.commit(data);
    assertNotNull(pageOne.getChildPage("SymLink"));

    request.addInput("rename", "SymLink");
  }


  @Test
  public void testNoPageAtPath() throws Exception {
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "NonExistingPage");
    Response response = invokeResponder();

    assertEquals(404, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("doesn't exist", content);
    assertSubString("Error Occured", content);
  }

  @Test
  public void testAddFailWhenLinkPathIsInvalid() throws Exception {
    WikiPage symlink = WikiPageUtil.addPage(pageOne, PathParser.parse("SymLink"));

    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "PageOne PageTwo");
    Response response = invokeResponder();

    assertEquals(404, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("doesn't exist", content);
    assertSubString("Error Occured", content);
  }

  @Test
  public void linkNameShouldBeAValidWikiWord() throws Exception {
    request.addInput("linkName", "Sym+link");
    request.addInput("linkPath", "PageTwo");
    Response response = invokeResponder();

    assertEquals(412, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("WikiWord", content);
  }

  @Test
  public void testAddFailWhenPageAlreadyHasChild() throws Exception {
    WikiPage symlink = WikiPageUtil.addPage(pageOne, PathParser.parse("SymLink"), "");

    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "PageTwo");
    Response response = invokeResponder();

    assertEquals(412, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("already has a child named SymLink", content);
    assertSubString("Error Occured", content);
  }


  @Test
  public void testSubmitFormForLinkToExternalRoot() throws Exception {
    // Ise canonical names, since that's how they will be resolved.
    fileSystem.makeDirectory(new File("/somedir"));
    fileSystem.makeDirectory(new File("/somedir/ExternalRoot"));

    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "file:/somedir/ExternalRoot");
    Response response = invokeResponder();

    checkPageOneRedirectToProperties(response);

    WikiPage symLink = pageOne.getChildPage("SymLink");
    assertNotNull(symLink);
    assertEquals(SymbolicPage.class, symLink.getClass());

    WikiPage realPage = ((SymbolicPage) symLink).getRealPage();
    assertEquals(FileSystemPage.class, realPage.getClass());
    assertEquals(new File("/somedir/ExternalRoot"), ((FileSystemPage) realPage).getFileSystemPath());
  }

  @Test
  public void testSubmitFormForLinkToExternalRootThatsMissing() throws Exception {
    request.addInput("linkName", "SymLink");
    request.addInput("linkPath", "file:/testDir/ExternalRoot");
    Response response = invokeResponder();

    assertEquals(404, response.getStatus());
    String content = ((SimpleResponse) response).getContent();
    assertSubString("Cannot create link to the file system path 'file:/testDir/ExternalRoot'.", content);
    assertSubString("Error Occured", content);
  }

  private void checkPageOneRedirectToProperties(Response response) {
    assertEquals(303, response.getStatus());
    assertEquals(response.getHeader("Location"), "/PageOne?properties#symbolics");
  }

  private void checkChildTwoRedirectToProperties(Response response) {
    assertEquals(303, response.getStatus());
    assertEquals(response.getHeader("Location"), "/PageTwo.ChildTwo?properties#symbolics");
  }
}
